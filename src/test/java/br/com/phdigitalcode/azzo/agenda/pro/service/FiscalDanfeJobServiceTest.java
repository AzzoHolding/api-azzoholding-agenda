package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Cobre {@code modules/fiscal/application/FiscalDanfeJobService.java}.
 *
 * <p>⚠️ {@code findByTenantAndExternalInvoiceId}, {@code findActiveJob} e {@code findByTenantAndId}
 * sao {@code default} nos repositorios — estubados diretamente, nunca via a query interna
 * (armadilha 7).
 */
@ExtendWith(MockitoExtension.class)
class FiscalDanfeJobServiceTest {

  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private FiscalInvoiceRepository fiscalInvoiceRepository;
  @Mock private FiscalDanfeJobRepository fiscalDanfeJobRepository;
  @Mock private FiscalJobWorker fiscalJobWorker;
  @Mock private MinioStorageService minioStorageService;
  @Mock private FiscalProvider fiscalProvider;
  @Mock private FiscalDanfeMapper fiscalDanfeMapper;
  @Mock private FiscalDanfeRenderService fiscalDanfeRenderService;

  private FiscalDanfeJobService service;
  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new FiscalDanfeJobService(
            contextoTenant,
            authenticatedUser,
            fiscalInvoiceRepository,
            fiscalDanfeJobRepository,
            fiscalJobWorker,
            minioStorageService,
            fiscalProvider,
            fiscalDanfeMapper,
            fiscalDanfeRenderService,
            20);
  }

  // ─── solicitarGeracao ───────────────────────────────────────────────────

  @Test
  void solicitarGeracaoLancaQuandoInvoiceNaoExiste() {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.solicitarGeracao("ext-1"))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Invoice fiscal nao encontrada.");
  }

  @Test
  void solicitarGeracaoDevolveOJobAtivoExistenteSemCriarNovo() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity ativo = jobComId();
    ativo.setStatus("PROCESSING");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));
    when(fiscalDanfeJobRepository.findActiveJob(tenantId, invoice.getId()))
        .thenReturn(Optional.of(ativo));

    FiscalDtos.DanfeJobResponse resposta = service.solicitarGeracao("ext-1");

    assertThat(resposta.jobId).isEqualTo(ativo.getId().toString());
    assertThat(resposta.status).isEqualTo("PROCESSING");
    verify(fiscalDanfeJobRepository, never()).save(any());
  }

  @Test
  void solicitarGeracaoCriaNovoJobQueuedComOUsuarioAtual() {
    FiscalInvoiceEntity invoice = invoiceComId();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));
    when(fiscalDanfeJobRepository.findActiveJob(tenantId, invoice.getId())).thenReturn(Optional.empty());
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    when(fiscalDanfeJobRepository.save(any(FiscalDanfeJobEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FiscalDtos.DanfeJobResponse resposta = service.solicitarGeracao("ext-1");

    ArgumentCaptor<FiscalDanfeJobEntity> captor = ArgumentCaptor.forClass(FiscalDanfeJobEntity.class);
    verify(fiscalDanfeJobRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getInvoiceId()).isEqualTo(invoice.getId());
    assertThat(captor.getValue().getRequestedBy()).isEqualTo(userId);
    assertThat(captor.getValue().getStatus()).isEqualTo("QUEUED");
    assertThat(resposta.status).isEqualTo("QUEUED");
  }

  // ─── consultarJob ───────────────────────────────────────────────────────

  @Test
  void consultarJobLancaQuandoJobNaoExiste() {
    FiscalInvoiceEntity invoice = invoiceComId();
    UUID jobId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));
    when(fiscalDanfeJobRepository.findByTenantAndId(tenantId, jobId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.consultarJob("ext-1", jobId.toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Job DANFE nao encontrado.");
  }

  @Test
  void consultarJobLancaQuandoJobPertenceAOutraInvoice() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(UUID.randomUUID());
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));
    when(fiscalDanfeJobRepository.findByTenantAndId(tenantId, job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.consultarJob("ext-1", job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Job DANFE nao encontrado para a invoice informada.");
  }

  @Test
  void consultarJobComJobIdInvalidoLancaIllegalArgument() {
    FiscalInvoiceEntity invoice = invoiceComId();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> service.consultarJob("ext-1", "nao-e-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobId invalido");
  }

  @Test
  void consultarJobDevolveDownloadAvailableTrueQuandoDoneENaoConsumido() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));
    when(fiscalDanfeJobRepository.findByTenantAndId(tenantId, job.getId())).thenReturn(Optional.of(job));

    FiscalDtos.DanfeJobResponse resposta = service.consultarJob("ext-1", job.getId().toString());

    assertThat(resposta.downloadAvailable).isTrue();
    assertThat(resposta.downloadConsumed).isFalse();
  }

  // ─── baixarDanfe ────────────────────────────────────────────────────────

  @Test
  void baixarDanfeLancaConflitoQuandoJobNaoEstaDone() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("PROCESSING");
    stubJobEInvoice(invoice, job);

    assertThatThrownBy(() -> service.baixarDanfe("ext-1", job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("DANFE ainda nao esta pronto para download.")
        .satisfies(ex -> assertThat(((ApiClientErrorException) ex).getStatus()).isEqualTo(409));
  }

  @Test
  void baixarDanfeLanca410QuandoJaConsumido() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setDownloadCount(1);
    job.setPdfStorageKey("key-1");
    stubJobEInvoice(invoice, job);

    assertThatThrownBy(() -> service.baixarDanfe("ext-1", job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("DANFE ja foi baixado e removido.")
        .satisfies(ex -> assertThat(((ApiClientErrorException) ex).getStatus()).isEqualTo(410));
  }

  @Test
  void baixarDanfeExpiradoRemoveOArquivoELanca410() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().minusSeconds(10));
    stubJobEInvoice(invoice, job);

    assertThatThrownBy(() -> service.baixarDanfe("ext-1", job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("DANFE expirado e removido apos 24 horas.");

    verify(minioStorageService).removerArquivoFiscalDanfe("key-1", job.getTenantId());
    assertThat(job.getPdfStorageKey()).isNull();
  }

  @Test
  void baixarDanfeComStorageHabilitadoBaixaDoMinio() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    stubJobEInvoice(invoice, job);
    when(minioStorageService.isStorageHabilitado()).thenReturn(true);
    when(minioStorageService.baixarArquivo("key-1", tenantId)).thenReturn("%PDF-conteudo".getBytes());

    byte[] arquivo = service.baixarDanfe("ext-1", job.getId().toString());

    assertThat(arquivo).isNotEmpty();
    assertThat(job.getDownloadCount()).isEqualTo(1);
    assertThat(job.getDownloadedAt()).isNotNull();
    assertThat(job.getPdfStorageKey()).isNull();
    verify(minioStorageService).removerArquivoFiscalDanfe("key-1", tenantId);
  }

  @Test
  void baixarDanfeComStorageDesabilitadoRendeOnTheFly() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    stubJobEInvoice(invoice, job);
    when(minioStorageService.isStorageHabilitado()).thenReturn(false);
    FiscalDtos.Invoice invoiceDto = new FiscalDtos.Invoice();
    FiscalDtos.TaxConfig taxConfig = new FiscalDtos.TaxConfig();
    FiscalDanfeMapper.DanfeViewModel vm = new FiscalDanfeMapper.DanfeViewModel();
    when(fiscalProvider.obterInvoice(tenantId, "ext-1")).thenReturn(invoiceDto);
    when(fiscalProvider.obterTaxConfig(tenantId)).thenReturn(taxConfig);
    when(fiscalDanfeMapper.map(invoiceDto, taxConfig)).thenReturn(vm);
    when(fiscalDanfeRenderService.render(tenantId, "ext-1", vm)).thenReturn("%PDF-onfly".getBytes());

    byte[] arquivo = service.baixarDanfe("ext-1", job.getId().toString());

    assertThat(new String(arquivo)).isEqualTo("%PDF-onfly");
  }

  @Test
  void baixarDanfeComArquivoVazioLanca410() {
    FiscalInvoiceEntity invoice = invoiceComId();
    FiscalDanfeJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    stubJobEInvoice(invoice, job);
    when(minioStorageService.isStorageHabilitado()).thenReturn(true);
    when(minioStorageService.baixarArquivo("key-1", tenantId)).thenReturn(new byte[0]);

    assertThatThrownBy(() -> service.baixarDanfe("ext-1", job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Arquivo DANFE indisponivel para download.");
  }

  // ─── processarPendentes ─────────────────────────────────────────────────

  @Test
  void processarPendentesDelegaAoWorkerComOBatchSizeConfigurado() {
    when(fiscalJobWorker.processarPendentes(20)).thenReturn(3);

    int processados = service.processarPendentes();

    assertThat(processados).isEqualTo(3);
    verify(fiscalJobWorker, times(1)).processarPendentes(20);
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private void stubJobEInvoice(FiscalInvoiceEntity invoice, FiscalDanfeJobEntity job) {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(fiscalInvoiceRepository.findByTenantAndExternalInvoiceId(tenantId, "ext-1"))
        .thenReturn(Optional.of(invoice));
    when(fiscalDanfeJobRepository.findByTenantAndId(tenantId, job.getId())).thenReturn(Optional.of(job));
  }

  private FiscalInvoiceEntity invoiceComId() {
    FiscalInvoiceEntity invoice = new FiscalInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(tenantId);
    invoice.setExternalInvoiceId("ext-1");
    return invoice;
  }

  private FiscalDanfeJobEntity jobComId() {
    FiscalDanfeJobEntity job = new FiscalDanfeJobEntity();
    job.setId(UUID.randomUUID());
    job.setTenantId(tenantId);
    job.setStatus("QUEUED");
    job.setDownloadCount(0);
    return job;
  }
}
