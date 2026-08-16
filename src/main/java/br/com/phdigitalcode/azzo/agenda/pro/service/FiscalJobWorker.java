package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalDanfeJobEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalDanfeJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;

/**
 * Porte verbatim de {@code modules/fiscal/application/FiscalJobWorker.java}.
 *
 * <p>Renderiza o PDF de cada job {@code QUEUED}, envia ao storage e marca {@code DONE}/{@code
 * ERROR}. Emite eventos de auditoria via {@link FiscalInvoiceEventService} (que ja falha em
 * silencio por desenho — nao muda o resultado do job).
 *
 * <p>⚠️ <b>Armadilha 5</b>: o original marca {@code processarJob} com {@code
 * Transactional.TxType.REQUIRES_NEW} e o chama a partir de {@code processarPendentes}, no mesmo
 * bean — isso e auto-invocacao. No Spring, {@code @Transactional} em auto-invocacao e ignorado em
 * silencio (o proxy nunca entra em cena), entao {@link #processarJob(UUID)} usa {@link
 * TransactionTemplate} explicito para garantir que cada job processa na sua propria transacao,
 * mesmo quando chamado de dentro de {@link #processarPendentes(int)}.
 */
@Service
public class FiscalJobWorker {

  private static final Logger LOG = LoggerFactory.getLogger(FiscalJobWorker.class);

  private final FiscalDanfeJobRepository fiscalDanfeJobRepository;
  private final FiscalInvoiceRepository fiscalInvoiceRepository;
  private final FiscalProvider fiscalProvider;
  private final FiscalDanfeMapper fiscalDanfeMapper;
  private final FiscalDanfeRenderService fiscalDanfeRenderService;
  private final MinioStorageService minioStorageService;
  private final FiscalInvoiceEventService fiscalInvoiceEventService;
  private final TransactionTemplate requiresNewTransactionTemplate;
  private final int danfeDownloadExpirationHours;

  public FiscalJobWorker(
      FiscalDanfeJobRepository fiscalDanfeJobRepository,
      FiscalInvoiceRepository fiscalInvoiceRepository,
      FiscalProvider fiscalProvider,
      FiscalDanfeMapper fiscalDanfeMapper,
      FiscalDanfeRenderService fiscalDanfeRenderService,
      MinioStorageService minioStorageService,
      FiscalInvoiceEventService fiscalInvoiceEventService,
      PlatformTransactionManager transactionManager,
      @Value("${app.fiscal.danfe.download-expiration-hours:24}") int danfeDownloadExpirationHours) {
    this.fiscalDanfeJobRepository = fiscalDanfeJobRepository;
    this.fiscalInvoiceRepository = fiscalInvoiceRepository;
    this.fiscalProvider = fiscalProvider;
    this.fiscalDanfeMapper = fiscalDanfeMapper;
    this.fiscalDanfeRenderService = fiscalDanfeRenderService;
    this.minioStorageService = minioStorageService;
    this.fiscalInvoiceEventService = fiscalInvoiceEventService;
    this.danfeDownloadExpirationHours = danfeDownloadExpirationHours;
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public int processarPendentes(int limit) {
    List<UUID> pendentes =
        fiscalDanfeJobRepository.findQueued(Math.max(limit, 1)).stream()
            .map(FiscalDanfeJobEntity::getId)
            .toList();
    int processados = 0;
    LOG.info("FiscalJobWorker iniciado. jobs={}", pendentes.size());
    for (UUID jobId : pendentes) {
      processados += processarJob(jobId);
    }
    LOG.info("FiscalJobWorker finalizado. jobsProcessados={}", processados);
    return processados;
  }

  public int processarJob(UUID jobId) {
    return requiresNewTransactionTemplate.execute(
        status -> {
          FiscalDanfeJobEntity job = fiscalDanfeJobRepository.findById(jobId).orElse(null);
          if (job == null) return 0;
          if (!fiscalDanfeJobRepository.tryLockByInvoice(job.getId(), job.getInvoiceId())) return 0;
          processarJobInterno(job);
          return 1;
        });
  }

  private void processarJobInterno(FiscalDanfeJobEntity job) {
    try {
      FiscalInvoiceEntity invoice =
          fiscalInvoiceRepository
              .findById(job.getInvoiceId())
              .orElseThrow(
                  () ->
                      new ApiClientErrorException(
                          "Invoice fiscal nao encontrada para processamento DANFE.", 404));

      FiscalDtos.Invoice invoiceDto =
          fiscalProvider.obterInvoice(job.getTenantId(), invoice.getExternalInvoiceId());
      FiscalDtos.TaxConfig taxConfig = fiscalProvider.obterTaxConfig(job.getTenantId());
      FiscalDanfeMapper.DanfeViewModel vm = fiscalDanfeMapper.map(invoiceDto, taxConfig);
      byte[] pdf =
          fiscalDanfeRenderService.render(job.getTenantId(), invoice.getExternalInvoiceId(), vm);
      String storageKey =
          minioStorageService.salvarArquivoFiscalDanfe(
              pdf, invoice.getExternalInvoiceId(), job.getTenantId());

      job.setPdfStorageKey(storageKey);
      job.setStatus("DONE");
      job.setErrorCode(null);
      job.setErrorMessage(null);
      job.setFinishedAt(Instant.now());
      job.setDownloadCount(0);
      job.setDownloadedAt(null);
      job.setDownloadExpiresAt(
          job.getFinishedAt().plusSeconds(Math.max(danfeDownloadExpirationHours, 1) * 3600L));

      fiscalInvoiceEventService.registrarEvento(
          job.getTenantId(),
          invoice.getId(),
          "FISCAL_DANFE_RENDER_SUCCESS",
          "SUCCESS",
          null,
          null);
    } catch (Exception e) {
      LOG.warn(
          "Falha ao processar job DANFE id={} tenant={}", job.getId(), job.getTenantId(), e);
      job.setStatus("ERROR");
      job.setErrorCode("DANFE_RENDER_ERROR");
      job.setErrorMessage(resumir(e.getMessage(), 500));
      job.setFinishedAt(Instant.now());

      fiscalInvoiceEventService.registrarEvento(
          job.getTenantId(),
          job.getInvoiceId(),
          "FISCAL_DANFE_RENDER_ERROR",
          "ERROR",
          "DANFE_RENDER_ERROR",
          resumir(e.getMessage(), 200));
    }
  }

  private String resumir(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.length() <= max) return normalized;
    return normalized.substring(0, max);
  }
}
