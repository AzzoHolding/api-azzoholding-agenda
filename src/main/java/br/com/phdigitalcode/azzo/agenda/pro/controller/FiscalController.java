package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalIdempotencyService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFiscal;

/**
 * Espelha {@code modules/fiscal/api/FiscalResource.java}.
 *
 * <p>Todas as rotas do original sao {@code @RolesAllowed({"OWNER"})} — incluindo as de
 * certificado, que repetiam a mesma role por metodo. Como nao ha faixa mista aqui (diferente de
 * {@code BillingController}), {@code @PreAuthorize} vai de classe.
 *
 * <p>As escritas de invoice ({@code criarInvoice}, {@code atualizarInvoice}, {@code cancelar},
 * {@code autorizar}, {@code reprocessarAutorizacao}) passam pela mesma dobra do original:
 * {@code fiscalAccessService.validarAcessoFiscal(...)} explicito seguido de
 * {@code fiscalIdempotencyService.execute(...)} com a chave de {@code X-Idempotency-Key}; as
 * demais rotas chamam {@link #validarAcessoFiscalAtual()}.
 *
 * <p><b>Rate limiting nao portado:</b> o original tem
 * {@code @RateLimited(bucket = "fiscal-api", identityResolver = FiscalIdentityResolver.class)} de
 * classe (extensao Quarkiverse Bucket4j, identidade {@code tenant:user:ip}). O Spring nao tem uma
 * extensao declarativa equivalente — {@code security.RateLimitFilter} existente e fixo para as
 * rotas de {@code /api/v1/auth/*} (login/register/forgot/reset-password), sem resolver de
 * identidade plugavel por-resource. Nao ha padrao ja estabelecido em outro controller portado para
 * reaproveitar; implementar isso ad-hoc aqui seria inventar um mecanismo novo fora do escopo desta
 * fronteira. Lacuna sinalizada, nao implementada.
 */
@RestController
@RequestMapping("/api/v1/fiscal")
@PreAuthorize("hasRole('OWNER')")
public class FiscalController {

  private final ServicoFiscal servicoFiscal;
  private final FiscalAccessService fiscalAccessService;
  private final FiscalIdempotencyService fiscalIdempotencyService;
  private final ContextoTenant contextoTenant;

  public FiscalController(
      ServicoFiscal servicoFiscal,
      FiscalAccessService fiscalAccessService,
      FiscalIdempotencyService fiscalIdempotencyService,
      ContextoTenant contextoTenant) {
    this.servicoFiscal = servicoFiscal;
    this.fiscalAccessService = fiscalAccessService;
    this.fiscalIdempotencyService = fiscalIdempotencyService;
    this.contextoTenant = contextoTenant;
  }

  @GetMapping("/tax-config")
  public FiscalDtos.TaxConfig obterTaxConfig() {
    validarAcessoFiscalAtual();
    return servicoFiscal.obterTaxConfig();
  }

  @PutMapping("/tax-config")
  public FiscalDtos.TaxConfig atualizarTaxConfig(@RequestBody FiscalDtos.TaxConfig request) {
    validarAcessoFiscalAtual();
    return servicoFiscal.atualizarTaxConfig(request);
  }

  @GetMapping("/invoices")
  public FiscalDtos.InvoiceListResponse listarInvoices(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    validarAcessoFiscalAtual();
    return servicoFiscal.listarInvoices(status, from, to, page, pageSize);
  }

  @GetMapping("/invoices/{id}")
  public FiscalDtos.Invoice obterInvoice(@PathVariable String id) {
    validarAcessoFiscalAtual();
    return servicoFiscal.obterInvoice(id);
  }

  @PostMapping("/invoices")
  public ResponseEntity<FiscalDtos.Invoice> criarInvoice(
      @RequestBody(required = false) FiscalDtos.Invoice request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    fiscalAccessService.validarAcessoFiscal(tenantId);
    FiscalDtos.Invoice response =
        fiscalIdempotencyService.execute(
            tenantId,
            "FISCAL_CREATE_INVOICE",
            key,
            () -> servicoFiscal.criarInvoice(request),
            FiscalDtos.Invoice.class);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/invoices/{id}")
  public FiscalDtos.Invoice atualizarInvoice(
      @PathVariable String id,
      @RequestBody(required = false) FiscalDtos.Invoice request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    fiscalAccessService.validarAcessoFiscal(tenantId);
    return fiscalIdempotencyService.execute(
        tenantId,
        "FISCAL_UPDATE_INVOICE_" + id,
        key,
        () -> servicoFiscal.atualizarInvoice(id, request),
        FiscalDtos.Invoice.class);
  }

  @PatchMapping("/invoices/{id}/cancel")
  public FiscalDtos.Invoice cancelar(
      @PathVariable String id,
      @RequestBody(required = false) FiscalDtos.CancelInvoiceRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    fiscalAccessService.validarAcessoFiscal(tenantId);
    return fiscalIdempotencyService.execute(
        tenantId,
        "FISCAL_CANCEL_INVOICE_" + id,
        key,
        () -> servicoFiscal.cancelarInvoice(id, request),
        FiscalDtos.Invoice.class);
  }

  @PostMapping("/invoices/{id}/authorize")
  public FiscalDtos.Invoice autorizar(
      @PathVariable String id,
      @RequestBody(required = false) FiscalDtos.AuthorizeInvoiceRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    fiscalAccessService.validarAcessoFiscal(tenantId);
    return fiscalIdempotencyService.execute(
        tenantId,
        "FISCAL_AUTHORIZE_INVOICE_" + id,
        key,
        () -> servicoFiscal.autorizarInvoice(id, request != null ? request.certificatePassword : null),
        FiscalDtos.Invoice.class);
  }

  @PostMapping("/invoices/{id}/reprocess-authorize")
  public FiscalDtos.Invoice reprocessarAutorizacao(
      @PathVariable String id,
      @RequestBody(required = false) FiscalDtos.AuthorizeInvoiceRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    fiscalAccessService.validarAcessoFiscal(tenantId);
    return fiscalIdempotencyService.execute(
        tenantId,
        "FISCAL_REPROCESS_AUTHORIZE_INVOICE_" + id,
        key,
        () ->
            servicoFiscal.reprocessarAutorizacaoInvoice(
                id, request != null ? request.certificatePassword : null),
        FiscalDtos.Invoice.class);
  }

  @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(@PathVariable String id) {
    validarAcessoFiscalAtual();
    return ResponseEntity.ok(servicoFiscal.gerarPdfInvoice(id));
  }

  @PostMapping("/invoices/{id}/pdf/jobs")
  public ResponseEntity<FiscalDtos.DanfeJobResponse> solicitarGeracaoDanfe(@PathVariable String id) {
    validarAcessoFiscalAtual();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(servicoFiscal.solicitarGeracaoDanfe(id));
  }

  @GetMapping("/invoices/{id}/pdf/jobs/{jobId}")
  public FiscalDtos.DanfeJobResponse consultarGeracaoDanfe(
      @PathVariable String id, @PathVariable String jobId) {
    validarAcessoFiscalAtual();
    return servicoFiscal.consultarJobDanfe(id, jobId);
  }

  @GetMapping(value = "/invoices/{id}/pdf/jobs/{jobId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> baixarDanfeJob(@PathVariable String id, @PathVariable String jobId) {
    validarAcessoFiscalAtual();
    byte[] pdf = servicoFiscal.baixarDanfeJob(id, jobId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"danfe-" + id + ".pdf\"")
        .body(pdf);
  }

  @GetMapping("/certificates")
  public List<FiscalDtos.CertificateResponse> listarCertificados() {
    validarAcessoFiscalAtual();
    return servicoFiscal.listarCertificados();
  }

  @PostMapping("/certificates")
  public ResponseEntity<FiscalDtos.CertificateResponse> salvarCertificado(
      @RequestBody(required = false) FiscalDtos.CertificateUpsertRequest request) {
    validarAcessoFiscalAtual();
    return ResponseEntity.status(HttpStatus.CREATED).body(servicoFiscal.salvarCertificado(request));
  }

  @PostMapping("/certificates/{id}/activate")
  public FiscalDtos.CertificateResponse ativarCertificado(@PathVariable String id) {
    validarAcessoFiscalAtual();
    return servicoFiscal.ativarCertificado(id);
  }

  @PatchMapping("/certificates/{id}/delete")
  public ResponseEntity<Void> removerCertificado(@PathVariable String id) {
    validarAcessoFiscalAtual();
    servicoFiscal.removerCertificado(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/apuracoes/current")
  public FiscalDtos.ApuracaoMensal apuracaoAtual() {
    validarAcessoFiscalAtual();
    return servicoFiscal.obterApuracaoAtual();
  }

  @GetMapping("/apuracoes/{ano}/{mes}")
  public FiscalDtos.ApuracaoMensal apuracaoPorMes(@PathVariable int ano, @PathVariable int mes) {
    validarAcessoFiscalAtual();
    return servicoFiscal.obterApuracao(ano, mes);
  }

  @PostMapping("/apuracoes/{ano}/{mes}/recalculate")
  public FiscalDtos.ApuracaoMensal recalcular(@PathVariable int ano, @PathVariable int mes) {
    validarAcessoFiscalAtual();
    return servicoFiscal.recalcularApuracao(ano, mes);
  }

  @GetMapping("/apuracoes/historico")
  public List<FiscalDtos.ApuracaoResumo> historico(@RequestParam(required = false) Integer limite) {
    validarAcessoFiscalAtual();
    int lim = limite == null ? 12 : limite;
    return servicoFiscal.historico(lim);
  }

  @GetMapping("/apuracoes/resumo-anual")
  public FiscalDtos.ResumoAnual resumoAnual(@RequestParam(required = false) Integer ano) {
    validarAcessoFiscalAtual();
    int valorAno = ano == null ? LocalDate.now().getYear() : ano;
    return servicoFiscal.resumoAnual(valorAno);
  }

  private void validarAcessoFiscalAtual() {
    fiscalAccessService.validarAcessoFiscal(contextoTenant.obterTenantIdOuFalhar());
  }
}
