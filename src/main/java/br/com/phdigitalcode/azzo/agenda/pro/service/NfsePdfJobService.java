package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfsePdfJobEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfsePdfJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Porte verbatim de {@code modules/nfse/application/NfsePdfJobService.java} (Fronteira 7).
 *
 * <p>Fachada de solicitacao/consulta/download do PDF da NFS-e — a geracao em si roda assincrona via
 * {@link NfsePdfJobWorker}. Mesmo esqueleto de {@link FiscalDanfeJobService} (Fronteira 5 de {@code
 * fiscal}), reusando os metodos genericos {@code salvarArquivoFiscalDanfe}/{@code
 * removerArquivoFiscalDanfe} de {@link MinioStorageService} — o original tambem reusa essas mesmas
 * chamadas para o storage do PDF de NFS-e, sem um metodo dedicado.
 *
 * <p>{@code jakarta.ws.rs.NotFoundException} vira {@link ApiClientErrorException} 404;
 * {@code WebApplicationException(CONFLICT)} vira 409; {@code WebApplicationException(GONE)} vira
 * 410 — mesmos status HTTP do original.
 */
@Service
public class NfsePdfJobService {

  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NfsePdfJobService.class);

  /** Gera o PDF logo apos o pedido; o agendador de 20 s fica so como reserva. */
  private final ExecutorService geracaoImediata = Executors.newVirtualThreadPerTaskExecutor();

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final NfseInvoiceRepository nfseInvoiceRepository;
  private final NfsePdfJobRepository nfsePdfJobRepository;
  private final NfsePdfJobWorker nfsePdfJobWorker;
  private final NfsePdfRenderService nfsePdfRenderService;
  private final MinioStorageService minioStorageService;
  private final int workerBatchSize;

  public NfsePdfJobService(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      NfseInvoiceRepository nfseInvoiceRepository,
      NfsePdfJobRepository nfsePdfJobRepository,
      NfsePdfJobWorker nfsePdfJobWorker,
      NfsePdfRenderService nfsePdfRenderService,
      MinioStorageService minioStorageService,
      @Value("${app.nfse.pdf.worker.batch-size:20}") int workerBatchSize) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.nfseInvoiceRepository = nfseInvoiceRepository;
    this.nfsePdfJobRepository = nfsePdfJobRepository;
    this.nfsePdfJobWorker = nfsePdfJobWorker;
    this.nfsePdfRenderService = nfsePdfRenderService;
    this.minioStorageService = minioStorageService;
    this.workerBatchSize = workerBatchSize;
  }

  @Transactional
  public NfseDtos.PdfJobResponse solicitarGeracao(String invoiceId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    NfseInvoiceEntity invoice = obterInvoiceOuFalhar(tenantId, invoiceId);
    // Com cancelamento pedido ou recusado, a nota continua valida na Receita: o PDF tambem sai.
    if (!java.util.EnumSet.of(
            NfseFiscalStatus.AUTHORIZED,
            NfseFiscalStatus.CANCEL_PENDING,
            NfseFiscalStatus.CANCEL_REJECTED,
            NfseFiscalStatus.CANCELLED)
        .contains(invoice.getFiscalStatus())) {
      throw new IllegalArgumentException("PDF da NFS-e só pode ser gerado para documento autorizado/cancelado.");
    }

    NfsePdfJobEntity activeJob = nfsePdfJobRepository.findActiveJob(tenantId, invoice.getId()).orElse(null);
    if (activeJob != null) return toResponse(activeJob);

    NfsePdfJobEntity job = new NfsePdfJobEntity();
    job.setTenantId(tenantId);
    job.setInvoiceId(invoice.getId());
    job.setRequestedBy(obterUsuarioAtualOuFalhar());
    job.setStatus("QUEUED");
    nfsePdfJobRepository.save(job);

    invoice.setOperationalStatus(NfseOperationalStatus.PROCESSING_PDF);
    gerarLogoAposGravar(job.getId());
    return toResponse(job);
  }

  /**
   * Antes o PDF so saia na proxima rodada do {@code NfsePdfJobScheduler} (ate 20 s de espera).
   * Agora o job roda assim que a transacao do pedido grava. Se falhar ou a aplicacao cair, o job
   * continua QUEUED e o agendador o pega; o lock logico do worker impede gerar duas vezes.
   */
  private void gerarLogoAposGravar(UUID jobId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        geracaoImediata.execute(() -> {
          try {
            nfsePdfJobWorker.processarJob(jobId);
          } catch (RuntimeException ex) {
            LOG.warn("nfse_pdf_geracao_imediata_falhou job_id={} (fica para o agendador)", jobId, ex);
          }
        });
      }
    });
  }

  @PreDestroy
  void encerrar() {
    geracaoImediata.shutdown();
  }

  @Transactional(readOnly = true)
  public NfseDtos.PdfJobResponse consultarJob(String invoiceId, String jobId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    NfseInvoiceEntity invoice = obterInvoiceOuFalhar(tenantId, invoiceId);
    UUID parsedJobId = parseUuid(jobId, "jobId invalido.");
    NfsePdfJobEntity job =
        nfsePdfJobRepository
            .findByTenantIdAndId(tenantId, parsedJobId)
            .orElseThrow(() -> new ApiClientErrorException("Job PDF NFS-e não encontrado.", 404));
    if (!invoice.getId().equals(job.getInvoiceId())) {
      throw new ApiClientErrorException("Job PDF NFS-e não encontrado para a invoice informada.", 404);
    }
    return toResponse(job);
  }

  @Transactional
  public byte[] baixarPdf(String invoiceId, String jobId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    NfseInvoiceEntity invoice = obterInvoiceOuFalhar(tenantId, invoiceId);
    UUID parsedJobId = parseUuid(jobId, "jobId invalido.");
    NfsePdfJobEntity job =
        nfsePdfJobRepository
            .findByTenantIdAndId(tenantId, parsedJobId)
            .orElseThrow(() -> new ApiClientErrorException("Job PDF NFS-e não encontrado.", 404));
    if (!invoice.getId().equals(job.getInvoiceId())) {
      throw new ApiClientErrorException("Job PDF NFS-e não encontrado para a invoice informada.", 404);
    }
    if (!"DONE".equalsIgnoreCase(job.getStatus())) {
      throw new ApiClientErrorException("PDF da NFS-e ainda não esta pronto para download.", 409);
    }
    if (isDownloadConsumido(job)) {
      throw new ApiClientErrorException("PDF da NFS-e já foi baixado e removido.", 410);
    }
    if (isDownloadExpirado(job)) {
      minioStorageService.removerArquivoFiscalDanfe(job.getPdfStorageKey(), job.getTenantId());
      job.setPdfStorageKey(null);
      invoice.setPdfStorageKey(null);
      throw new ApiClientErrorException("PDF da NFS-e expirado e removido após 24 horas.", 410);
    }

    byte[] arquivo =
        minioStorageService.isStorageHabilitado()
            ? minioStorageService.baixarArquivo(job.getPdfStorageKey(), tenantId)
            : nfsePdfRenderService.render(invoice, null);
    if (arquivo == null || arquivo.length == 0) {
      throw new ApiClientErrorException("Arquivo PDF da NFS-e indisponível para download.", 410);
    }

    job.setDownloadCount((job.getDownloadCount() == null ? 0 : job.getDownloadCount()) + 1);
    job.setDownloadedAt(Instant.now());
    minioStorageService.removerArquivoFiscalDanfe(job.getPdfStorageKey(), job.getTenantId());
    job.setPdfStorageKey(null);

    invoice.setPdfStorageKey(null);
    return arquivo;
  }

  public int processarPendentes() {
    return nfsePdfJobWorker.processarPendentes(Math.max(workerBatchSize, 1));
  }

  private NfseInvoiceEntity obterInvoiceOuFalhar(UUID tenantId, String invoiceId) {
    UUID id = parseUuid(invoiceId, "id da NFS-e invalido.");
    return nfseInvoiceRepository
        .findByTenantAndId(tenantId, id)
        .orElseThrow(() -> new ApiClientErrorException("NFS-e não encontrada.", 404));
  }

  private UUID obterUsuarioAtualOuFalhar() {
    return authenticatedUser.idOuFalhar();
  }

  private UUID parseUuid(String value, String message) {
    try {
      return UUID.fromString(value);
    } catch (Exception ex) {
      throw new IllegalArgumentException(message);
    }
  }

  private NfseDtos.PdfJobResponse toResponse(NfsePdfJobEntity job) {
    NfseDtos.PdfJobResponse response = new NfseDtos.PdfJobResponse();
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

  private boolean isDownloadConsumido(NfsePdfJobEntity job) {
    int count = job.getDownloadCount() == null ? 0 : job.getDownloadCount();
    return count > 0
        || job.getDownloadedAt() != null
        || job.getPdfStorageKey() == null
        || job.getPdfStorageKey().isBlank();
  }

  private boolean isDownloadExpirado(NfsePdfJobEntity job) {
    return job.getDownloadExpiresAt() != null && Instant.now().isAfter(job.getDownloadExpiresAt());
  }
}
