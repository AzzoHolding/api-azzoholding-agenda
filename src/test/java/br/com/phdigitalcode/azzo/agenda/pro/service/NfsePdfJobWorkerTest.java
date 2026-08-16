package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfsePdfJobEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfsePdfJobRepository;

/**
 * Cobre {@code modules/nfse/application/NfsePdfJobWorker.java} (Fronteira 7).
 *
 * <p>O original nao tem teste proprio no Quarkus. Mesmo esqueleto de {@link FiscalJobWorkerTest}
 * (Fronteira 5 de {@code fiscal}), que ja prova o padrao {@code TransactionTemplate} +
 * {@code REQUIRES_NEW} para contornar a auto-invocacao (armadilha 4/5 do briefing).
 *
 * <p>{@code tryLockByInvoice}/{@code findQueued} sao {@code default} em {@link
 * NfsePdfJobRepository} — estubados diretamente (armadilha 7).
 */
@ExtendWith(MockitoExtension.class)
class NfsePdfJobWorkerTest {

  @Mock private NfsePdfJobRepository nfsePdfJobRepository;
  @Mock private NfseInvoiceRepository nfseInvoiceRepository;
  @Mock private NfseInvoiceItemRepository nfseInvoiceItemRepository;
  @Mock private NfsePdfRenderService nfsePdfRenderService;
  @Mock private MinioStorageService minioStorageService;
  @Mock private PlatformTransactionManager transactionManager;

  private NfsePdfJobWorker worker;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    worker =
        new NfsePdfJobWorker(
            nfsePdfJobRepository,
            nfseInvoiceRepository,
            nfseInvoiceItemRepository,
            nfsePdfRenderService,
            minioStorageService,
            transactionManager,
            24);
  }

  @Test
  void processarJobDevolveZeroQuandoJobNaoExiste() {
    UUID jobId = UUID.randomUUID();
    when(nfsePdfJobRepository.findById(jobId)).thenReturn(Optional.empty());

    int resultado = worker.processarJob(jobId);

    assertThat(resultado).isZero();
  }

  @Test
  void processarJobDevolveZeroQuandoLockFalha() {
    NfsePdfJobEntity job = jobQueued();
    when(nfsePdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(nfsePdfJobRepository.tryLockByInvoice(job.getId(), job.getTenantId(), job.getInvoiceId()))
        .thenReturn(false);

    int resultado = worker.processarJob(job.getId());

    assertThat(resultado).isZero();
    verify(nfseInvoiceRepository, never()).findById(any());
  }

  @Test
  void processarJobComSucessoMarcaDoneLimpaOperationalStatusEEnviaParaOStorage() {
    NfsePdfJobEntity job = jobQueued();
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(job.getInvoiceId());
    List<NfseInvoiceItemEntity> items = List.of(new NfseInvoiceItemEntity());
    byte[] pdf = "%PDF-fake".getBytes();

    when(nfsePdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(nfsePdfJobRepository.tryLockByInvoice(job.getId(), job.getTenantId(), job.getInvoiceId()))
        .thenReturn(true);
    when(nfseInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.of(invoice));
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(job.getTenantId(), job.getInvoiceId()))
        .thenReturn(items);
    when(nfsePdfRenderService.render(invoice, items)).thenReturn(pdf);
    when(minioStorageService.salvarArquivoFiscalDanfe(pdf, invoice.getId().toString(), job.getTenantId()))
        .thenReturn("storage-key-1");

    int resultado = worker.processarJob(job.getId());

    assertThat(resultado).isEqualTo(1);
    assertThat(job.getStatus()).isEqualTo("DONE");
    assertThat(job.getPdfStorageKey()).isEqualTo("storage-key-1");
    assertThat(job.getErrorCode()).isNull();
    assertThat(job.getErrorMessage()).isNull();
    assertThat(job.getFinishedAt()).isNotNull();
    assertThat(job.getDownloadCount()).isZero();
    assertThat(job.getDownloadedAt()).isNull();
    assertThat(job.getDownloadExpiresAt()).isAfter(job.getFinishedAt());
    assertThat(invoice.getPdfStorageKey()).isEqualTo("storage-key-1");
    assertThat(invoice.getPdfGeneratedAt()).isEqualTo(job.getFinishedAt());
    assertThat(invoice.getOperationalStatus()).isNull();
  }

  @Test
  void processarJobComFalhaMarcaErrorEInvoiceComoPdfError() {
    NfsePdfJobEntity job = jobQueued();
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(job.getInvoiceId());
    when(nfsePdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(nfsePdfJobRepository.tryLockByInvoice(job.getId(), job.getTenantId(), job.getInvoiceId()))
        .thenReturn(true);
    when(nfseInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.of(invoice));
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(job.getTenantId(), job.getInvoiceId()))
        .thenReturn(List.of());
    when(nfsePdfRenderService.render(any(), any())).thenThrow(new RuntimeException("falha ao renderizar"));

    int resultado = worker.processarJob(job.getId());

    assertThat(resultado).isEqualTo(1);
    assertThat(job.getStatus()).isEqualTo("ERROR");
    assertThat(job.getErrorCode()).isEqualTo("NFSE_PDF_RENDER_ERROR");
    assertThat(job.getErrorMessage()).isEqualTo("falha ao renderizar");
    assertThat(job.getFinishedAt()).isNotNull();
    assertThat(invoice.getOperationalStatus()).isEqualTo(NfseOperationalStatus.PDF_ERROR);
  }

  @Test
  void processarJobComInvoiceAusenteMarcaError() {
    NfsePdfJobEntity job = jobQueued();
    when(nfsePdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(nfsePdfJobRepository.tryLockByInvoice(job.getId(), job.getTenantId(), job.getInvoiceId()))
        .thenReturn(true);
    when(nfseInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.empty());

    int resultado = worker.processarJob(job.getId());

    assertThat(resultado).isEqualTo(1);
    assertThat(job.getStatus()).isEqualTo("ERROR");
    assertThat(job.getErrorCode()).isEqualTo("NFSE_PDF_RENDER_ERROR");
  }

  @Test
  void mensagemDeErroLongaEResumidaEm500Caracteres() {
    NfsePdfJobEntity job = jobQueued();
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(job.getInvoiceId());
    String mensagemLonga = "x".repeat(600);

    when(nfsePdfJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(nfsePdfJobRepository.tryLockByInvoice(job.getId(), job.getTenantId(), job.getInvoiceId()))
        .thenReturn(true);
    when(nfseInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.of(invoice));
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(job.getTenantId(), job.getInvoiceId()))
        .thenReturn(List.of());
    when(nfsePdfRenderService.render(any(), any())).thenThrow(new RuntimeException(mensagemLonga));

    worker.processarJob(job.getId());

    assertThat(job.getErrorMessage()).hasSize(500);
  }

  @Test
  void processarPendentesProcessaTodosOsJobsDaFilaERetornaOTotal() {
    NfsePdfJobEntity job1 = jobQueued();
    NfsePdfJobEntity job2 = jobQueued();
    when(nfsePdfJobRepository.findQueued(20)).thenReturn(List.of(job1, job2));
    when(nfsePdfJobRepository.findById(job1.getId())).thenReturn(Optional.of(job1));
    when(nfsePdfJobRepository.findById(job2.getId())).thenReturn(Optional.of(job2));
    when(nfsePdfJobRepository.tryLockByInvoice(any(), any(), any())).thenReturn(false);

    int processados = worker.processarPendentes(20);

    assertThat(processados).isZero();
    verify(nfsePdfJobRepository, times(2)).findById(any());
  }

  @Test
  void processarPendentesUsaPisoDeUmParaOLimite() {
    when(nfsePdfJobRepository.findQueued(anyInt())).thenReturn(List.of());

    worker.processarPendentes(0);

    verify(nfsePdfJobRepository).findQueued(1);
  }

  private NfsePdfJobEntity jobQueued() {
    NfsePdfJobEntity job = new NfsePdfJobEntity();
    job.setId(UUID.randomUUID());
    job.setTenantId(tenantId);
    job.setInvoiceId(UUID.randomUUID());
    job.setStatus("QUEUED");
    return job;
  }
}
