package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalDanfeJobEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MinioStorageService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalDanfeJobRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;

/**
 * Cobre {@code modules/fiscal/application/FiscalJobWorker.java}.
 *
 * <p>⚠️ {@code findQueued}, {@code tryLockByInvoice} e {@code findById} usados aqui: os dois
 * primeiros sao {@code default} em {@link FiscalDanfeJobRepository} — estubados diretamente
 * (armadilha 7), nunca via a query interna.
 *
 * <p>{@link PlatformTransactionManager} e mockado sem stub de {@code getTransaction}: o
 * {@code TransactionTemplate} construido no worker aceita o retorno {@code null} do mock e ainda
 * assim executa a callback normalmente — nao ha transacao real de banco no teste unitario, so a
 * garantia de que o metodo roda dentro do {@code TransactionTemplate.execute(...)}.
 */
@ExtendWith(MockitoExtension.class)
class FiscalJobWorkerTest {

  @Mock private FiscalDanfeJobRepository fiscalDanfeJobRepository;
  @Mock private FiscalInvoiceRepository fiscalInvoiceRepository;
  @Mock private FiscalProvider fiscalProvider;
  @Mock private FiscalDanfeMapper fiscalDanfeMapper;
  @Mock private FiscalDanfeRenderService fiscalDanfeRenderService;
  @Mock private MinioStorageService minioStorageService;
  @Mock private FiscalInvoiceEventService fiscalInvoiceEventService;
  @Mock private PlatformTransactionManager transactionManager;

  private FiscalJobWorker worker;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    worker =
        new FiscalJobWorker(
            fiscalDanfeJobRepository,
            fiscalInvoiceRepository,
            fiscalProvider,
            fiscalDanfeMapper,
            fiscalDanfeRenderService,
            minioStorageService,
            fiscalInvoiceEventService,
            transactionManager,
            24);
  }

  @Test
  void processarJobDevolveZeroQuandoJobNaoExiste() {
    UUID jobId = UUID.randomUUID();
    when(fiscalDanfeJobRepository.findById(jobId)).thenReturn(Optional.empty());

    int resultado = worker.processarJob(jobId);

    assertThat(resultado).isZero();
  }

  @Test
  void processarJobDevolveZeroQuandoLockFalha() {
    FiscalDanfeJobEntity job = jobQueued();
    when(fiscalDanfeJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(fiscalDanfeJobRepository.tryLockByInvoice(job.getId(), job.getInvoiceId())).thenReturn(false);

    int resultado = worker.processarJob(job.getId());

    assertThat(resultado).isZero();
    verify(fiscalInvoiceRepository, never()).findById(any());
  }

  @Test
  void processarJobComSucessoMarcaDoneEEnviaParaOStorage() {
    FiscalDanfeJobEntity job = jobQueued();
    FiscalInvoiceEntity invoice = new FiscalInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setExternalInvoiceId("ext-1");
    FiscalDtos.Invoice invoiceDto = new FiscalDtos.Invoice();
    FiscalDtos.TaxConfig taxConfig = new FiscalDtos.TaxConfig();
    FiscalDanfeMapper.DanfeViewModel vm = new FiscalDanfeMapper.DanfeViewModel();
    byte[] pdf = "%PDF-fake".getBytes();

    when(fiscalDanfeJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(fiscalDanfeJobRepository.tryLockByInvoice(job.getId(), job.getInvoiceId())).thenReturn(true);
    when(fiscalInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.of(invoice));
    when(fiscalProvider.obterInvoice(tenantId, "ext-1")).thenReturn(invoiceDto);
    when(fiscalProvider.obterTaxConfig(tenantId)).thenReturn(taxConfig);
    when(fiscalDanfeMapper.map(invoiceDto, taxConfig)).thenReturn(vm);
    when(fiscalDanfeRenderService.render(tenantId, "ext-1", vm)).thenReturn(pdf);
    when(minioStorageService.salvarArquivoFiscalDanfe(pdf, "ext-1", tenantId)).thenReturn("storage-key-1");

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

    verify(fiscalInvoiceEventService)
        .registrarEvento(tenantId, invoice.getId(), "FISCAL_DANFE_RENDER_SUCCESS", "SUCCESS", null, null);
  }

  @Test
  void processarJobComFalhaMarcaErrorERegistraEvento() {
    FiscalDanfeJobEntity job = jobQueued();
    when(fiscalDanfeJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(fiscalDanfeJobRepository.tryLockByInvoice(job.getId(), job.getInvoiceId())).thenReturn(true);
    when(fiscalInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.empty());

    int resultado = worker.processarJob(job.getId());

    assertThat(resultado).isEqualTo(1);
    assertThat(job.getStatus()).isEqualTo("ERROR");
    assertThat(job.getErrorCode()).isEqualTo("DANFE_RENDER_ERROR");
    assertThat(job.getErrorMessage()).isNotBlank();
    assertThat(job.getFinishedAt()).isNotNull();

    verify(fiscalInvoiceEventService)
        .registrarEvento(
            eq(tenantId),
            eq(job.getInvoiceId()),
            eq("FISCAL_DANFE_RENDER_ERROR"),
            eq("ERROR"),
            eq("DANFE_RENDER_ERROR"),
            any());
  }

  @Test
  void mensagemDeErroLongaEResumidaEm500Caracteres() {
    FiscalDanfeJobEntity job = jobQueued();
    FiscalInvoiceEntity invoice = new FiscalInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setExternalInvoiceId("ext-1");
    String mensagemLonga = "x".repeat(600);

    when(fiscalDanfeJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(fiscalDanfeJobRepository.tryLockByInvoice(job.getId(), job.getInvoiceId())).thenReturn(true);
    when(fiscalInvoiceRepository.findById(job.getInvoiceId())).thenReturn(Optional.of(invoice));
    when(fiscalProvider.obterInvoice(tenantId, "ext-1"))
        .thenThrow(new RuntimeException(mensagemLonga));

    worker.processarJob(job.getId());

    assertThat(job.getErrorMessage()).hasSize(500);
  }

  @Test
  void processarPendentesProcessaTodosOsJobsDaFilaERetornaOTotal() {
    FiscalDanfeJobEntity job1 = jobQueued();
    FiscalDanfeJobEntity job2 = jobQueued();
    when(fiscalDanfeJobRepository.findQueued(20)).thenReturn(List.of(job1, job2));
    when(fiscalDanfeJobRepository.findById(job1.getId())).thenReturn(Optional.of(job1));
    when(fiscalDanfeJobRepository.findById(job2.getId())).thenReturn(Optional.of(job2));
    when(fiscalDanfeJobRepository.tryLockByInvoice(any(), any())).thenReturn(false);

    int processados = worker.processarPendentes(20);

    assertThat(processados).isZero();
    verify(fiscalDanfeJobRepository, times(2)).findById(any());
  }

  @Test
  void processarPendentesUsaPisoDeUmParaOLimite() {
    when(fiscalDanfeJobRepository.findQueued(anyInt())).thenReturn(List.of());

    worker.processarPendentes(0);

    verify(fiscalDanfeJobRepository).findQueued(1);
  }

  private FiscalDanfeJobEntity jobQueued() {
    FiscalDanfeJobEntity job = new FiscalDanfeJobEntity();
    job.setId(UUID.randomUUID());
    job.setTenantId(tenantId);
    job.setInvoiceId(UUID.randomUUID());
    job.setStatus("QUEUED");
    return job;
  }
}
