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

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfsePdfJobEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfsePdfJobRepository;

/**
 * Porte verbatim de {@code modules/nfse/application/NfsePdfJobWorker.java} (Fronteira 7).
 *
 * <p>⚠️ <b>Armadilha 4/5 do briefing</b>: o original marca {@code processarJob} com {@code
 * Transactional.TxType.REQUIRES_NEW} e o chama a partir de {@code processarPendentes}, no mesmo
 * bean — auto-invocacao. No Spring isso e ignorado em silencio, entao {@link #processarJob(UUID)}
 * usa {@link TransactionTemplate} explicito, mesmo padrao ja resolvido em {@link FiscalJobWorker}
 * (Fronteira 5 de {@code fiscal}).
 *
 * <p>{@code tryLockByInvoice} continua sendo o lock <b>logico</b> via {@code UPDATE} condicional de
 * {@link NfsePdfJobRepository} (nao pessimista) — preservado 1:1, nao trocado pelo lock pessimista
 * que {@link NfseInvoiceRepository} usa em outro contexto (armadilha 11).
 */
@Service
public class NfsePdfJobWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NfsePdfJobWorker.class);

  private final NfsePdfJobRepository nfsePdfJobRepository;
  private final NfseInvoiceRepository nfseInvoiceRepository;
  private final NfseInvoiceItemRepository nfseInvoiceItemRepository;
  private final NfsePdfRenderService nfsePdfRenderService;
  private final MinioStorageService minioStorageService;
  private final TransactionTemplate requiresNewTransactionTemplate;
  private final int downloadExpirationHours;

  public NfsePdfJobWorker(
      NfsePdfJobRepository nfsePdfJobRepository,
      NfseInvoiceRepository nfseInvoiceRepository,
      NfseInvoiceItemRepository nfseInvoiceItemRepository,
      NfsePdfRenderService nfsePdfRenderService,
      MinioStorageService minioStorageService,
      PlatformTransactionManager transactionManager,
      @Value("${app.nfse.pdf.download-expiration-hours:24}") int downloadExpirationHours) {
    this.nfsePdfJobRepository = nfsePdfJobRepository;
    this.nfseInvoiceRepository = nfseInvoiceRepository;
    this.nfseInvoiceItemRepository = nfseInvoiceItemRepository;
    this.nfsePdfRenderService = nfsePdfRenderService;
    this.minioStorageService = minioStorageService;
    this.downloadExpirationHours = downloadExpirationHours;
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public int processarPendentes(int limit) {
    List<UUID> pendentes =
        nfsePdfJobRepository.findQueued(Math.max(limit, 1)).stream().map(NfsePdfJobEntity::getId).toList();
    int processados = 0;
    LOG.info("NfsePdfJobWorker iniciado. jobs={}", pendentes.size());
    for (UUID jobId : pendentes) {
      processados += processarJob(jobId);
    }
    LOG.info("NfsePdfJobWorker finalizado. jobsProcessados={}", processados);
    return processados;
  }

  public int processarJob(UUID jobId) {
    Integer resultado =
        requiresNewTransactionTemplate.execute(
            status -> {
              NfsePdfJobEntity job = nfsePdfJobRepository.findById(jobId).orElse(null);
              if (job == null) return 0;
              if (!nfsePdfJobRepository.tryLockByInvoice(job.getId(), job.getTenantId(), job.getInvoiceId())) {
                return 0;
              }
              processarJobInterno(job);
              return 1;
            });
    return resultado == null ? 0 : resultado;
  }

  private void processarJobInterno(NfsePdfJobEntity job) {
    try {
      NfseInvoiceEntity invoice =
          nfseInvoiceRepository
              .findById(job.getInvoiceId())
              .orElseThrow(() -> new ApiClientErrorException("NFS-e nao encontrada para processamento do PDF.", 404));
      invoice.setOperationalStatus(NfseOperationalStatus.PROCESSING_PDF);
      List<NfseInvoiceItemEntity> items =
          nfseInvoiceItemRepository.listByTenantAndInvoice(job.getTenantId(), job.getInvoiceId());
      byte[] pdf = nfsePdfRenderService.render(invoice, items);
      String storageKey =
          minioStorageService.salvarArquivoFiscalDanfe(pdf, invoice.getId().toString(), job.getTenantId());

      job.setStatus("DONE");
      job.setErrorCode(null);
      job.setErrorMessage(null);
      job.setPdfStorageKey(storageKey);
      job.setFinishedAt(Instant.now());
      job.setDownloadCount(0);
      job.setDownloadedAt(null);
      job.setDownloadExpiresAt(job.getFinishedAt().plusSeconds(Math.max(downloadExpirationHours, 1) * 3600L));

      invoice.setPdfStorageKey(storageKey);
      invoice.setPdfGeneratedAt(job.getFinishedAt());
      invoice.setOperationalStatus(null);
    } catch (Exception ex) {
      LOG.warn("Falha ao processar PDF da NFS-e job={} tenant={}", job.getId(), job.getTenantId(), ex);
      job.setStatus("ERROR");
      job.setErrorCode("NFSE_PDF_RENDER_ERROR");
      job.setErrorMessage(resumir(ex.getMessage(), 500));
      job.setFinishedAt(Instant.now());
      nfseInvoiceRepository
          .findById(job.getInvoiceId())
          .ifPresent(invoice -> invoice.setOperationalStatus(NfseOperationalStatus.PDF_ERROR));
    }
  }

  private String resumir(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }
}
