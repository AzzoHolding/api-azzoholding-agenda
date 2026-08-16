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
 * Cobre {@code modules/nfse/application/NfsePdfJobService.java} (Fronteira 7).
 *
 * <p>O original nao tem teste proprio no Quarkus (confirmado por {@code find} antes de escrever
 * este arquivo — nenhum {@code NfsePdfJob*Test} existe em {@code azzo-agenda-pro}). Este teste
 * segue o mesmo esqueleto de {@link FiscalDanfeJobServiceTest} (Fronteira 5 de {@code fiscal}),
 * que cobre o mesmo padrao de fachada solicitar/consultar/baixar.
 *
 * <p>⚠️ {@code findByTenantAndId}, {@code findActiveJob} sao {@code default} em {@link
 * NfsePdfJobRepository} — estubados diretamente (armadilha 7 do briefing).
 */
@ExtendWith(MockitoExtension.class)
class NfsePdfJobServiceTest {

  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private NfseInvoiceRepository nfseInvoiceRepository;
  @Mock private NfsePdfJobRepository nfsePdfJobRepository;
  @Mock private NfsePdfJobWorker nfsePdfJobWorker;
  @Mock private NfsePdfRenderService nfsePdfRenderService;
  @Mock private MinioStorageService minioStorageService;

  private NfsePdfJobService service;
  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new NfsePdfJobService(
            contextoTenant,
            authenticatedUser,
            nfseInvoiceRepository,
            nfsePdfJobRepository,
            nfsePdfJobWorker,
            nfsePdfRenderService,
            minioStorageService,
            20);
  }

  // ─── solicitarGeracao ───────────────────────────────────────────────────

  @Test
  void solicitarGeracaoLancaQuandoInvoiceNaoExiste() {
    UUID invoiceId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.solicitarGeracao(invoiceId.toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("NFS-e nao encontrada.");
  }

  @Test
  void solicitarGeracaoComIdInvalidoLancaIllegalArgument() {
    assertThatThrownBy(() -> service.solicitarGeracao("nao-e-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("id da NFS-e invalido.");
  }

  @Test
  void solicitarGeracaoRejeitaInvoiceEmStatusNaoElegivel() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.DRAFT);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> service.solicitarGeracao(invoice.getId().toString()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PDF da NFS-e so pode ser gerado para documento autorizado/cancelado.");
  }

  @Test
  void solicitarGeracaoDevolveOJobAtivoExistenteSemCriarNovo() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity ativo = jobComId();
    ativo.setStatus("PROCESSING");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
    when(nfsePdfJobRepository.findActiveJob(tenantId, invoice.getId())).thenReturn(Optional.of(ativo));

    NfseDtos.PdfJobResponse resposta = service.solicitarGeracao(invoice.getId().toString());

    assertThat(resposta.jobId).isEqualTo(ativo.getId().toString());
    assertThat(resposta.status).isEqualTo("PROCESSING");
    verify(nfsePdfJobRepository, never()).save(any());
  }

  @Test
  void solicitarGeracaoParaInvoiceCanceladaCriaNovoJobQueuedComOUsuarioAtual() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.CANCELLED);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
    when(nfsePdfJobRepository.findActiveJob(tenantId, invoice.getId())).thenReturn(Optional.empty());
    when(authenticatedUser.idOuFalhar()).thenReturn(userId);
    when(nfsePdfJobRepository.save(any(NfsePdfJobEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    NfseDtos.PdfJobResponse resposta = service.solicitarGeracao(invoice.getId().toString());

    ArgumentCaptor<NfsePdfJobEntity> captor = ArgumentCaptor.forClass(NfsePdfJobEntity.class);
    verify(nfsePdfJobRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getInvoiceId()).isEqualTo(invoice.getId());
    assertThat(captor.getValue().getRequestedBy()).isEqualTo(userId);
    assertThat(captor.getValue().getStatus()).isEqualTo("QUEUED");
    assertThat(resposta.status).isEqualTo("QUEUED");
    assertThat(invoice.getOperationalStatus()).isEqualTo(NfseOperationalStatus.PROCESSING_PDF);
  }

  // ─── consultarJob ───────────────────────────────────────────────────────

  @Test
  void consultarJobLancaQuandoJobNaoExiste() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    UUID jobId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
    when(nfsePdfJobRepository.findByTenantIdAndId(tenantId, jobId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.consultarJob(invoice.getId().toString(), jobId.toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Job PDF NFS-e nao encontrado.");
  }

  @Test
  void consultarJobLancaQuandoJobPertenceAOutraInvoice() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(UUID.randomUUID());
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
    when(nfsePdfJobRepository.findByTenantIdAndId(tenantId, job.getId())).thenReturn(Optional.of(job));

    assertThatThrownBy(() -> service.consultarJob(invoice.getId().toString(), job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Job PDF NFS-e nao encontrado para a invoice informada.");
  }

  @Test
  void consultarJobComJobIdInvalidoLancaIllegalArgument() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> service.consultarJob(invoice.getId().toString(), "nao-e-uuid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobId invalido.");
  }

  @Test
  void consultarJobDevolveDownloadAvailableTrueQuandoDoneENaoConsumido() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
    when(nfsePdfJobRepository.findByTenantIdAndId(tenantId, job.getId())).thenReturn(Optional.of(job));

    NfseDtos.PdfJobResponse resposta = service.consultarJob(invoice.getId().toString(), job.getId().toString());

    assertThat(resposta.downloadAvailable).isTrue();
    assertThat(resposta.downloadConsumed).isFalse();
  }

  // ─── baixarPdf ──────────────────────────────────────────────────────────

  @Test
  void baixarPdfLancaConflitoQuandoJobNaoEstaDone() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("PROCESSING");
    stubJobEInvoice(invoice, job);

    assertThatThrownBy(() -> service.baixarPdf(invoice.getId().toString(), job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("PDF da NFS-e ainda nao esta pronto para download.")
        .satisfies(ex -> assertThat(((ApiClientErrorException) ex).getStatus()).isEqualTo(409));
  }

  @Test
  void baixarPdfLanca410QuandoJaConsumido() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setDownloadCount(1);
    job.setPdfStorageKey("key-1");
    stubJobEInvoice(invoice, job);

    assertThatThrownBy(() -> service.baixarPdf(invoice.getId().toString(), job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("PDF da NFS-e ja foi baixado e removido.")
        .satisfies(ex -> assertThat(((ApiClientErrorException) ex).getStatus()).isEqualTo(410));
  }

  @Test
  void baixarPdfExpiradoRemoveOArquivoELimpaAInvoiceELanca410() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    invoice.setPdfStorageKey("key-1");
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().minusSeconds(10));
    stubJobEInvoice(invoice, job);

    assertThatThrownBy(() -> service.baixarPdf(invoice.getId().toString(), job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("PDF da NFS-e expirado e removido apos 24 horas.");

    verify(minioStorageService).removerArquivoFiscalDanfe("key-1", job.getTenantId());
    assertThat(job.getPdfStorageKey()).isNull();
    assertThat(invoice.getPdfStorageKey()).isNull();
  }

  @Test
  void baixarPdfComStorageHabilitadoBaixaDoMinio() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    stubJobEInvoice(invoice, job);
    when(minioStorageService.isStorageHabilitado()).thenReturn(true);
    when(minioStorageService.baixarArquivo("key-1", tenantId)).thenReturn("%PDF-conteudo".getBytes());

    byte[] arquivo = service.baixarPdf(invoice.getId().toString(), job.getId().toString());

    assertThat(arquivo).isNotEmpty();
    assertThat(job.getDownloadCount()).isEqualTo(1);
    assertThat(job.getDownloadedAt()).isNotNull();
    assertThat(job.getPdfStorageKey()).isNull();
    assertThat(invoice.getPdfStorageKey()).isNull();
    verify(minioStorageService).removerArquivoFiscalDanfe("key-1", tenantId);
  }

  @Test
  void baixarPdfComStorageDesabilitadoRendeOnTheFly() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    stubJobEInvoice(invoice, job);
    when(minioStorageService.isStorageHabilitado()).thenReturn(false);
    when(nfsePdfRenderService.render(invoice, null)).thenReturn("%PDF-onfly".getBytes());

    byte[] arquivo = service.baixarPdf(invoice.getId().toString(), job.getId().toString());

    assertThat(new String(arquivo)).isEqualTo("%PDF-onfly");
  }

  @Test
  void baixarPdfComArquivoVazioLanca410() {
    NfseInvoiceEntity invoice = invoiceComId(NfseFiscalStatus.AUTHORIZED);
    NfsePdfJobEntity job = jobComId();
    job.setInvoiceId(invoice.getId());
    job.setStatus("DONE");
    job.setPdfStorageKey("key-1");
    job.setDownloadExpiresAt(Instant.now().plusSeconds(3600));
    stubJobEInvoice(invoice, job);
    when(minioStorageService.isStorageHabilitado()).thenReturn(true);
    when(minioStorageService.baixarArquivo("key-1", tenantId)).thenReturn(new byte[0]);

    assertThatThrownBy(() -> service.baixarPdf(invoice.getId().toString(), job.getId().toString()))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Arquivo PDF da NFS-e indisponivel para download.");
  }

  // ─── processarPendentes ─────────────────────────────────────────────────

  @Test
  void processarPendentesDelegaAoWorkerComOBatchSizeConfigurado() {
    when(nfsePdfJobWorker.processarPendentes(20)).thenReturn(3);

    int processados = service.processarPendentes();

    assertThat(processados).isEqualTo(3);
    verify(nfsePdfJobWorker, times(1)).processarPendentes(20);
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private void stubJobEInvoice(NfseInvoiceEntity invoice, NfsePdfJobEntity job) {
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
    when(nfsePdfJobRepository.findByTenantIdAndId(tenantId, job.getId())).thenReturn(Optional.of(job));
  }

  private NfseInvoiceEntity invoiceComId(NfseFiscalStatus status) {
    NfseInvoiceEntity invoice = new NfseInvoiceEntity();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(tenantId);
    invoice.setFiscalStatus(status);
    return invoice;
  }

  private NfsePdfJobEntity jobComId() {
    NfsePdfJobEntity job = new NfsePdfJobEntity();
    job.setId(UUID.randomUUID());
    job.setTenantId(tenantId);
    job.setStatus("QUEUED");
    job.setDownloadCount(0);
    return job;
  }
}
