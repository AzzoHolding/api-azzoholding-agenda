package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalIdempotencyService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFiscal;

/**
 * Espelha {@code modules/fiscal/api/FiscalResource.java} e o contrato de
 * {@code FiscalResourceSecurityContractUnitTest} (a parte que tem equivalente Spring: role e
 * rota/verbo). {@code @RateLimited(bucket = "fiscal-api", identityResolver =
 * FiscalIdentityResolver.class)} do original nao tem par no Spring — {@link FiscalController} ja
 * documenta essa lacuna (sem extensao Bucket4j declarativa reaproveitavel), entao nao ha aqui um
 * teste equivalente ao de rate limit do original.
 */
class FiscalControllerTest {

  private final UUID tenantId = UUID.randomUUID();

  private ServicoFiscal servicoFiscal;
  private FiscalAccessService fiscalAccessService;
  private FiscalIdempotencyService fiscalIdempotencyService;
  private ContextoTenant contextoTenant;
  private FiscalController controller;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    servicoFiscal = mock(ServicoFiscal.class);
    fiscalAccessService = mock(FiscalAccessService.class);
    fiscalIdempotencyService = mock(FiscalIdempotencyService.class);
    contextoTenant = mock(ContextoTenant.class);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);

    // fiscalIdempotencyService.execute delega direto para o supplier, como faria sem chave de
    // idempotencia informada (mesmo efeito pratico para os testes de delegacao abaixo).
    when(fiscalIdempotencyService.execute(any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(3)).get());

    controller = new FiscalController(servicoFiscal, fiscalAccessService, fiscalIdempotencyService, contextoTenant);
  }

  // ─── contrato de classe ─────────────────────────────────────────────────

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(FiscalController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/fiscal");
  }

  @Test
  void classeMantemRolePermitidaDoOriginal() {
    PreAuthorize preAuthorize = FiscalController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).isEqualTo("hasRole('OWNER')");
  }

  @Test
  void cadaRotaMantemVerboECaminhoDoOriginal() {
    assertGet("obterTaxConfig", "/tax-config");
    assertPut("atualizarTaxConfig", "/tax-config");
    assertGet("listarInvoices", "/invoices");
    assertGet("obterInvoice", "/invoices/{id}");
    assertPost("criarInvoice", "/invoices");
    assertPatch("atualizarInvoice", "/invoices/{id}");
    assertPatch("cancelar", "/invoices/{id}/cancel");
    assertPost("autorizar", "/invoices/{id}/authorize");
    assertPost("reprocessarAutorizacao", "/invoices/{id}/reprocess-authorize");
    assertGet("pdf", "/invoices/{id}/pdf");
    assertPost("solicitarGeracaoDanfe", "/invoices/{id}/pdf/jobs");
    assertGet("consultarGeracaoDanfe", "/invoices/{id}/pdf/jobs/{jobId}");
    assertGet("baixarDanfeJob", "/invoices/{id}/pdf/jobs/{jobId}/download");
    assertGet("listarCertificados", "/certificates");
    assertPost("salvarCertificado", "/certificates");
    assertPost("ativarCertificado", "/certificates/{id}/activate");
    assertPatch("removerCertificado", "/certificates/{id}/delete");
    assertGet("apuracaoAtual", "/apuracoes/current");
    assertGet("apuracaoPorMes", "/apuracoes/{ano}/{mes}");
    assertPost("recalcular", "/apuracoes/{ano}/{mes}/recalculate");
    assertGet("historico", "/apuracoes/historico");
    assertGet("resumoAnual", "/apuracoes/resumo-anual");
  }

  @Test
  void rotaDePdfProduzApplicationPdf() throws NoSuchMethodException {
    Method m = FiscalController.class.getDeclaredMethod("pdf", String.class);
    assertThat(m.getAnnotation(GetMapping.class).produces())
        .containsExactly(MediaType.APPLICATION_PDF_VALUE);
  }

  // ─── delegacao simples (sem idempotencia) ────────────────────────────────

  @Test
  void rotasSimplesValidamAcessoEDelegamParaServicoFiscal() {
    controller.obterTaxConfig();
    controller.listarInvoices("AUTHORIZED", "2026-01-01", "2026-01-31", 1, 20);
    controller.obterInvoice("inv-001");
    controller.listarCertificados();
    controller.apuracaoAtual();
    controller.apuracaoPorMes(2026, 1);
    controller.recalcular(2026, 1);
    controller.historico(6);
    controller.resumoAnual(2026);

    verify(fiscalAccessService, org.mockito.Mockito.times(9)).validarAcessoFiscal(tenantId);
    verify(servicoFiscal).obterTaxConfig();
    verify(servicoFiscal).listarInvoices("AUTHORIZED", "2026-01-01", "2026-01-31", 1, 20);
    verify(servicoFiscal).obterInvoice("inv-001");
    verify(servicoFiscal).listarCertificados();
    verify(servicoFiscal).obterApuracaoAtual();
    verify(servicoFiscal).obterApuracao(2026, 1);
    verify(servicoFiscal).recalcularApuracao(2026, 1);
    verify(servicoFiscal).historico(6);
    verify(servicoFiscal).resumoAnual(2026);
  }

  @Test
  void historicoSemLimiteUsaDefaultDoze() {
    controller.historico(null);

    verify(servicoFiscal).historico(12);
  }

  @Test
  void resumoAnualSemAnoUsaAnoCorrente() {
    controller.resumoAnual(null);

    verify(servicoFiscal).resumoAnual(anyInt());
  }

  // ─── rotas com idempotencia ───────────────────────────────────────────────

  @Test
  void criarInvoiceValidaAcessoAntesDeExecutarEUsaChaveDeOperacaoFixa() {
    FiscalDtos.Invoice request = new FiscalDtos.Invoice();
    FiscalDtos.Invoice resposta = new FiscalDtos.Invoice();
    when(servicoFiscal.criarInvoice(request)).thenReturn(resposta);

    ResponseEntity<FiscalDtos.Invoice> response = controller.criarInvoice(request, "idem-key-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isSameAs(resposta);
    verify(fiscalAccessService).validarAcessoFiscal(tenantId);
    verify(fiscalIdempotencyService)
        .execute(eq(tenantId), eq("FISCAL_CREATE_INVOICE"), eq("idem-key-1"), any(), eq(FiscalDtos.Invoice.class));
  }

  @Test
  void atualizarInvoiceUsaChaveDeOperacaoComIdDaInvoice() {
    FiscalDtos.Invoice request = new FiscalDtos.Invoice();
    controller.atualizarInvoice("inv-42", request, "idem-key-2");

    verify(fiscalAccessService).validarAcessoFiscal(tenantId);
    verify(fiscalIdempotencyService)
        .execute(
            eq(tenantId), eq("FISCAL_UPDATE_INVOICE_inv-42"), eq("idem-key-2"), any(), eq(FiscalDtos.Invoice.class));
    verify(servicoFiscal).atualizarInvoice("inv-42", request);
  }

  @Test
  void cancelarUsaChaveDeOperacaoComIdDaInvoice() {
    FiscalDtos.CancelInvoiceRequest request = new FiscalDtos.CancelInvoiceRequest();
    controller.cancelar("inv-42", request, null);

    verify(fiscalIdempotencyService)
        .execute(
            eq(tenantId), eq("FISCAL_CANCEL_INVOICE_inv-42"), isNull(), any(), eq(FiscalDtos.Invoice.class));
    verify(servicoFiscal).cancelarInvoice("inv-42", request);
  }

  @Test
  void autorizarDesmontaSenhaDoCertificadoDoRequestBody() {
    FiscalDtos.AuthorizeInvoiceRequest request = new FiscalDtos.AuthorizeInvoiceRequest();
    request.certificatePassword = "senha-secreta";

    controller.autorizar("inv-42", request, null);

    verify(servicoFiscal).autorizarInvoice("inv-42", "senha-secreta");
  }

  @Test
  void autorizarComCorpoAusenteEnviaSenhaNula() {
    controller.autorizar("inv-42", null, null);

    verify(servicoFiscal).autorizarInvoice("inv-42", null);
  }

  @Test
  void reprocessarAutorizacaoDesmontaSenhaDoCertificadoDoRequestBody() {
    FiscalDtos.AuthorizeInvoiceRequest request = new FiscalDtos.AuthorizeInvoiceRequest();
    request.certificatePassword = "senha-secreta";

    controller.reprocessarAutorizacao("inv-42", request, null);

    verify(servicoFiscal).reprocessarAutorizacaoInvoice("inv-42", "senha-secreta");
  }

  // ─── PDF / DANFE ────────────────────────────────────────────────────────

  @Test
  void baixarDanfeJobDevolveContentDispositionComIdDaInvoice() {
    when(servicoFiscal.baixarDanfeJob("inv-42", "job-1")).thenReturn(new byte[] {1, 2, 3});

    ResponseEntity<byte[]> response = controller.baixarDanfeJob("inv-42", "job-1");

    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .isEqualTo("attachment; filename=\"danfe-inv-42.pdf\"");
    assertThat(response.getBody()).containsExactly(1, 2, 3);
  }

  @Test
  void solicitarGeracaoDanfeRetorna202Accepted() {
    FiscalDtos.DanfeJobResponse job = new FiscalDtos.DanfeJobResponse();
    when(servicoFiscal.solicitarGeracaoDanfe("inv-42")).thenReturn(job);

    ResponseEntity<FiscalDtos.DanfeJobResponse> response = controller.solicitarGeracaoDanfe("inv-42");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isSameAs(job);
  }

  @Test
  void salvarCertificadoRetorna201Created() {
    FiscalDtos.CertificateUpsertRequest request = new FiscalDtos.CertificateUpsertRequest();
    FiscalDtos.CertificateResponse resposta = new FiscalDtos.CertificateResponse();
    when(servicoFiscal.salvarCertificado(request)).thenReturn(resposta);

    ResponseEntity<FiscalDtos.CertificateResponse> response = controller.salvarCertificado(request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isSameAs(resposta);
  }

  @Test
  void removerCertificadoRetorna204SemConteudo() {
    ResponseEntity<Void> response = controller.removerCertificado("cert-1");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(servicoFiscal).removerCertificado("cert-1");
  }

  // ─── helpers de reflexao ──────────────────────────────────────────────────

  private void assertGet(String metodo, String caminho) {
    GetMapping mapping = metodo(metodo).getAnnotation(GetMapping.class);
    assertThat(mapping).as(metodo).isNotNull();
    assertThat(mapping.value()).as(metodo).containsExactly(caminho);
  }

  private void assertPost(String metodo, String caminho) {
    PostMapping mapping = metodo(metodo).getAnnotation(PostMapping.class);
    assertThat(mapping).as(metodo).isNotNull();
    assertThat(mapping.value()).as(metodo).containsExactly(caminho);
  }

  private void assertPut(String metodo, String caminho) {
    PutMapping mapping = metodo(metodo).getAnnotation(PutMapping.class);
    assertThat(mapping).as(metodo).isNotNull();
    assertThat(mapping.value()).as(metodo).containsExactly(caminho);
  }

  private void assertPatch(String metodo, String caminho) {
    PatchMapping mapping = metodo(metodo).getAnnotation(PatchMapping.class);
    assertThat(mapping).as(metodo).isNotNull();
    assertThat(mapping.value()).as(metodo).containsExactly(caminho);
  }

  private Method metodo(String nome) {
    for (Method m : FiscalController.class.getDeclaredMethods()) {
      if (m.getName().equals(nome)) return m;
    }
    throw new AssertionError("Metodo nao encontrado no FiscalController: " + nome);
  }
}
