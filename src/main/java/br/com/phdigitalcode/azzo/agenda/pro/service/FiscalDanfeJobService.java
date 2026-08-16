package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalDanfeJobEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalDanfeJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalDanfeJobService.java}.
 *
 * <p>Fachada de solicitacao/consulta/download do PDF do DANFE. A geracao em si roda assincrona via
 * {@link FiscalJobWorker} — este service so enfileira, consulta status e serve o download (ou
 * renderiza on-the-fly quando o storage esta desabilitado, caminho de desenvolvimento).
 *
 * <p>{@code jakarta.ws.rs.NotFoundException} vira {@link ApiClientErrorException} 404;
 * {@code WebApplicationException(CONFLICT)} vira 409; {@code WebApplicationException(GONE)} vira
 * 410 — mesmos status HTTP do original.
 */
@Service
public class FiscalDanfeJobService {

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final FiscalInvoiceRepository fiscalInvoiceRepository;
  private final FiscalDanfeJobRepository fiscalDanfeJobRepository;
  private final FiscalJobWorker fiscalJobWorker;
  private final MinioStorageService minioStorageService;
  private final FiscalProvider fiscalProvider;
  private final FiscalDanfeMapper fiscalDanfeMapper;
  private final FiscalDanfeRenderService fiscalDanfeRenderService;
  private final int workerBatchSize;

  public FiscalDanfeJobService(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      FiscalInvoiceRepository fiscalInvoiceRepository,
      FiscalDanfeJobRepository fiscalDanfeJobRepository,
      FiscalJobWorker fiscalJobWorker,
      MinioStorageService minioStorageService,
      FiscalProvider fiscalProvider,
      FiscalDanfeMapper fiscalDanfeMapper,
      FiscalDanfeRenderService fiscalDanfeRenderService,
      @Value("${app.fiscal.danfe.worker.batch-size:20}") int workerBatchSize) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.fiscalInvoiceRepository = fiscalInvoiceRepository;
    this.fiscalDanfeJobRepository = fiscalDanfeJobRepository;
    this.fiscalJobWorker = fiscalJobWorker;
    this.minioStorageService = minioStorageService;
    this.fiscalProvider = fiscalProvider;
    this.fiscalDanfeMapper = fiscalDanfeMapper;
    this.fiscalDanfeRenderService = fiscalDanfeRenderService;
    this.workerBatchSize = workerBatchSize;
  }

  @Transactional
  public FiscalDtos.DanfeJobResponse solicitarGeracao(String externalInvoiceId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalInvoiceEntity invoice = obterInvoiceOuFalhar(tenantId, externalInvoiceId);

    FiscalDanfeJobEntity ativo =
        fiscalDanfeJobRepository.findActiveJob(tenantId, invoice.getId()).orElse(null);
    if (ativo != null) return toResponse(ativo);

    FiscalDanfeJobEntity job = new FiscalDanfeJobEntity();
    job.setTenantId(tenantId);
    job.setInvoiceId(invoice.getId());
    job.setRequestedBy(obterUsuarioAtualOuFalhar());
    job.setStatus("QUEUED");
    fiscalDanfeJobRepository.save(job);
    return toResponse(job);
  }

  @Transactional(readOnly = true)
  public FiscalDtos.DanfeJobResponse consultarJob(String externalInvoiceId, String jobId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalInvoiceEntity invoice = obterInvoiceOuFalhar(tenantId, externalInvoiceId);
    UUID id = parseUuidOuFalhar(jobId, "jobId invalido");
    FiscalDanfeJobEntity job =
        fiscalDanfeJobRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("Job DANFE nao encontrado.", 404));
    if (!invoice.getId().equals(job.getInvoiceId())) {
      throw new ApiClientErrorException("Job DANFE nao encontrado para a invoice informada.", 404);
    }
    return toResponse(job);
  }

  @Transactional
  public byte[] baixarDanfe(String externalInvoiceId, String jobId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalInvoiceEntity invoice = obterInvoiceOuFalhar(tenantId, externalInvoiceId);
    UUID id = parseUuidOuFalhar(jobId, "jobId invalido");
    FiscalDanfeJobEntity job =
        fiscalDanfeJobRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("Job DANFE nao encontrado.", 404));
    if (!invoice.getId().equals(job.getInvoiceId())) {
      throw new ApiClientErrorException("Job DANFE nao encontrado para a invoice informada.", 404);
    }
    if (!"DONE".equalsIgnoreCase(job.getStatus())) {
      throw new ApiClientErrorException("DANFE ainda nao esta pronto para download.", 409);
    }
    if (Boolean.TRUE.equals(isDownloadConsumido(job))) {
      throw new ApiClientErrorException("DANFE ja foi baixado e removido.", 410);
    }
    if (isDownloadExpirado(job)) {
      minioStorageService.removerArquivoFiscalDanfe(job.getPdfStorageKey(), job.getTenantId());
      job.setPdfStorageKey(null);
      throw new ApiClientErrorException("DANFE expirado e removido apos 24 horas.", 410);
    }

    byte[] arquivo =
        minioStorageService.isStorageHabilitado()
            ? minioStorageService.baixarArquivo(job.getPdfStorageKey(), tenantId)
            : fiscalDanfeRenderService.render(
                tenantId,
                externalInvoiceId,
                fiscalDanfeMapper.map(
                    fiscalProvider.obterInvoice(tenantId, externalInvoiceId),
                    fiscalProvider.obterTaxConfig(tenantId)));
    if (arquivo == null || arquivo.length == 0) {
      throw new ApiClientErrorException("Arquivo DANFE indisponivel para download.", 410);
    }

    job.setDownloadCount((job.getDownloadCount() == null ? 0 : job.getDownloadCount()) + 1);
    job.setDownloadedAt(Instant.now());
    minioStorageService.removerArquivoFiscalDanfe(job.getPdfStorageKey(), job.getTenantId());
    job.setPdfStorageKey(null);
    return arquivo;
  }

  public int processarPendentes() {
    return fiscalJobWorker.processarPendentes(Math.max(workerBatchSize, 1));
  }

  private FiscalInvoiceEntity obterInvoiceOuFalhar(UUID tenantId, String externalInvoiceId) {
    return fiscalInvoiceRepository
        .findByTenantAndExternalInvoiceId(tenantId, externalInvoiceId)
        .orElseThrow(() -> new ApiClientErrorException("Invoice fiscal nao encontrada.", 404));
  }

  private UUID obterUsuarioAtualOuFalhar() {
    return authenticatedUser.idOuFalhar();
  }

  private UUID parseUuidOuFalhar(String value, String message) {
    try {
      return UUID.fromString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(message);
    }
  }

  private FiscalDtos.DanfeJobResponse toResponse(FiscalDanfeJobEntity job) {
    FiscalDtos.DanfeJobResponse response = new FiscalDtos.DanfeJobResponse();
    response.jobId = job.getId() != null ? job.getId().toString() : null;
    response.invoiceId = job.getInvoiceId() != null ? job.getInvoiceId().toString() : null;
    response.status = job.getStatus();
    response.errorCode = job.getErrorCode();
    response.errorMessage = job.getErrorMessage();
    response.requestedAt = job.getRequestedAt() != null ? job.getRequestedAt().toString() : null;
    response.finishedAt = job.getFinishedAt() != null ? job.getFinishedAt().toString() : null;
    response.downloadConsumed = isDownloadConsumido(job);
    response.downloadExpiresAt =
        job.getDownloadExpiresAt() != null ? job.getDownloadExpiresAt().toString() : null;
    response.downloadAvailable =
        "DONE".equalsIgnoreCase(job.getStatus())
            && !Boolean.TRUE.equals(response.downloadConsumed)
            && !isDownloadExpirado(job)
            && job.getPdfStorageKey() != null
            && !job.getPdfStorageKey().isBlank();
    return response;
  }

  private boolean isDownloadConsumido(FiscalDanfeJobEntity job) {
    int count = job.getDownloadCount() == null ? 0 : job.getDownloadCount();
    return count > 0
        || job.getDownloadedAt() != null
        || job.getPdfStorageKey() == null
        || job.getPdfStorageKey().isBlank();
  }

  private boolean isDownloadExpirado(FiscalDanfeJobEntity job) {
    return job.getDownloadExpiresAt() != null && Instant.now().isAfter(job.getDownloadExpiresAt());
  }
}
