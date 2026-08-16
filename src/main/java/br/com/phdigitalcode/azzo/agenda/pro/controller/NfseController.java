package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseCertificateUnlockService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseConfigService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseIdempotencyService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseLocationService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfsePdfJobService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseProviderCapabilitiesService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NfseService;

/**
 * Espelha {@code modules/nfse/api/NfseResource.java} (Fronteiras 6 e 7, ver
 * {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/27/28). Todas as rotas do original sao
 * {@code @RolesAllowed({"OWNER"})} — vira {@code @PreAuthorize} de classe, mesmo padrao de {@link
 * FiscalController}.
 *
 * <p>Config e provider-capabilities (fatias de CRUD que ja eram delegadas pelo {@code NfseService}
 * original para si mesmo) sao injetadas aqui direto de {@link NfseConfigService}/{@link
 * NfseProviderCapabilitiesService} — extraidas como servicos proprios na Fronteira 2, sem passar
 * pelo orquestrador {@link NfseService} (Fronteira 6), que so cuida de invoice/autorizacao/
 * cancelamento/export/lookup.
 *
 * <p><b>18/18 rotas do original portadas</b> — as 3 de PDF job ({@code POST
 * /invoices/{id}/pdf/jobs}, {@code GET /invoices/{id}/pdf/jobs/{jobId}}, {@code GET
 * /invoices/{id}/pdf/jobs/{jobId}/download}) entraram na Fronteira 7, delegando a {@link
 * NfsePdfJobService}.
 *
 * <p><b>Rate limiting nao portado</b> — mesma lacuna sinalizada em {@link FiscalController}
 * (o original tem {@code @RateLimited(bucket = "fiscal-api")} de classe via Quarkiverse Bucket4j,
 * sem equivalente declarativo no Spring deste projeto).
 */
@RestController
@RequestMapping("/api/v1/fiscal/nfse")
@PreAuthorize("hasRole('OWNER')")
public class NfseController {

  private final NfseService nfseService;
  private final FiscalAccessService fiscalAccessService;
  private final NfseCertificateUnlockService nfseCertificateUnlockService;
  private final NfseIdempotencyService nfseIdempotencyService;
  private final NfseLocationService nfseLocationService;
  private final NfseConfigService nfseConfigService;
  private final NfseProviderCapabilitiesService nfseProviderCapabilitiesService;
  private final NfsePdfJobService nfsePdfJobService;
  private final ContextoTenant contextoTenant;

  public NfseController(
      NfseService nfseService,
      FiscalAccessService fiscalAccessService,
      NfseCertificateUnlockService nfseCertificateUnlockService,
      NfseIdempotencyService nfseIdempotencyService,
      NfseLocationService nfseLocationService,
      NfseConfigService nfseConfigService,
      NfseProviderCapabilitiesService nfseProviderCapabilitiesService,
      NfsePdfJobService nfsePdfJobService,
      ContextoTenant contextoTenant) {
    this.nfseService = nfseService;
    this.fiscalAccessService = fiscalAccessService;
    this.nfseCertificateUnlockService = nfseCertificateUnlockService;
    this.nfseIdempotencyService = nfseIdempotencyService;
    this.nfseLocationService = nfseLocationService;
    this.nfseConfigService = nfseConfigService;
    this.nfseProviderCapabilitiesService = nfseProviderCapabilitiesService;
    this.nfsePdfJobService = nfsePdfJobService;
    this.contextoTenant = contextoTenant;
  }

  @GetMapping("/config")
  public NfseDtos.Config obterConfig(@RequestParam(required = false) String ambiente) {
    validarAcessoFiscalAtual();
    return nfseConfigService.obterConfig(ambiente);
  }

  @PutMapping("/config")
  public NfseDtos.Config salvarConfig(@RequestBody NfseDtos.Config request) {
    validarAcessoFiscalAtual();
    return nfseConfigService.salvarConfig(request);
  }

  @GetMapping("/states")
  public List<NfseDtos.FiscalState> listarEstados() {
    validarAcessoFiscalAtual();
    return nfseLocationService.listarEstados();
  }

  @GetMapping("/municipalities")
  public List<NfseDtos.FiscalMunicipality> listarMunicipios(
      @RequestParam(required = false) String stateUf,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer limit) {
    validarAcessoFiscalAtual();
    return nfseLocationService.listarMunicipios(stateUf, search, limit);
  }

  @PostMapping("/invoices")
  public ResponseEntity<NfseDtos.Invoice> criarRascunho(
      @RequestBody(required = false) NfseDtos.Invoice request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    validarAcessoFiscalAtual();
    NfseDtos.Invoice response =
        nfseIdempotencyService.execute(
            contextoTenant.obterTenantIdOuFalhar(),
            "NFSE_CREATE_INVOICE",
            key,
            () -> nfseService.criarRascunho(request),
            NfseDtos.Invoice.class);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/invoices/{id}")
  public NfseDtos.Invoice atualizarRascunho(
      @PathVariable String id,
      @RequestBody(required = false) NfseDtos.Invoice request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    validarAcessoFiscalAtual();
    return nfseIdempotencyService.execute(
        contextoTenant.obterTenantIdOuFalhar(),
        "NFSE_UPDATE_INVOICE_" + id,
        key,
        () -> nfseService.atualizarRascunho(id, request),
        NfseDtos.Invoice.class);
  }

  @GetMapping("/invoices/{id}")
  public NfseDtos.Invoice obterInvoice(@PathVariable String id) {
    validarAcessoFiscalAtual();
    return nfseService.obterInvoice(id);
  }

  @PostMapping("/invoices/{id}/authorize")
  public ResponseEntity<NfseDtos.Invoice> autorizar(
      @PathVariable String id,
      @RequestBody(required = false) NfseDtos.AuthorizeRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    validarAcessoFiscalAtual();
    NfseDtos.Invoice response =
        nfseIdempotencyService.execute(
            contextoTenant.obterTenantIdOuFalhar(),
            "NFSE_AUTHORIZE_INVOICE_" + id,
            key,
            () -> nfseService.autorizar(id, request),
            NfseDtos.Invoice.class);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @PostMapping("/invoices/{id}/cancel")
  public ResponseEntity<NfseDtos.Invoice> cancelar(
      @PathVariable String id,
      @RequestBody(required = false) NfseDtos.CancelRequest request,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    validarAcessoFiscalAtual();
    NfseDtos.Invoice response =
        nfseIdempotencyService.execute(
            contextoTenant.obterTenantIdOuFalhar(),
            "NFSE_CANCEL_INVOICE_" + id,
            key,
            () -> nfseService.cancelar(id, request),
            NfseDtos.Invoice.class);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping("/invoices")
  public NfseDtos.InvoiceListResponse listarInvoices(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    validarAcessoFiscalAtual();
    return nfseService.listarInvoices(status, page, pageSize);
  }

  @GetMapping("/invoices/accounting-export")
  public ResponseEntity<byte[]> exportarContabil(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String format) {
    validarAcessoFiscalAtual();
    NfseService.AccountingExportFile export = nfseService.exportacaoContabil(from, to, status, format);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(export.mediaType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.fileName() + "\"")
        .header("X-Export-Row-Count", String.valueOf(export.rowsCount()))
        .header("X-Export-Format", export.format())
        .body(export.content());
  }

  @GetMapping("/tomador/cnpj/{cnpj}")
  public NfseDtos.TomadorLookupResponse consultarTomadorPorCnpj(@PathVariable String cnpj) {
    validarAcessoFiscalAtual();
    return nfseService.consultarTomadorPorCnpj(cnpj);
  }

  @GetMapping("/provider-capabilities")
  public List<NfseDtos.ProviderCapabilities> listarProviderCapabilities(
      @RequestParam(required = false) String municipioCodigoIbge,
      @RequestParam(required = false) String provedor) {
    validarAcessoFiscalAtual();
    return nfseProviderCapabilitiesService.listarProviderCapabilities(municipioCodigoIbge, provedor);
  }

  @PutMapping("/provider-capabilities")
  public NfseDtos.ProviderCapabilities salvarProviderCapabilities(
      @RequestBody NfseDtos.ProviderCapabilities request) {
    validarAcessoFiscalAtual();
    return nfseProviderCapabilitiesService.salvarProviderCapabilities(request);
  }

  @PostMapping("/invoices/{id}/pdf/jobs")
  public ResponseEntity<NfseDtos.PdfJobResponse> solicitarGeracaoPdf(
      @PathVariable String id,
      @RequestHeader(value = "X-Idempotency-Key", required = false) String key) {
    validarAcessoFiscalAtual();
    NfseDtos.PdfJobResponse response =
        nfseIdempotencyService.execute(
            contextoTenant.obterTenantIdOuFalhar(),
            "NFSE_PDF_JOB_CREATE_" + id,
            key,
            () -> nfsePdfJobService.solicitarGeracao(id),
            NfseDtos.PdfJobResponse.class);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping("/invoices/{id}/pdf/jobs/{jobId}")
  public NfseDtos.PdfJobResponse consultarJobPdf(@PathVariable String id, @PathVariable String jobId) {
    validarAcessoFiscalAtual();
    return nfsePdfJobService.consultarJob(id, jobId);
  }

  @GetMapping(value = "/invoices/{id}/pdf/jobs/{jobId}/download", produces = "application/pdf")
  public ResponseEntity<byte[]> baixarPdf(@PathVariable String id, @PathVariable String jobId) {
    validarAcessoFiscalAtual();
    byte[] pdf = nfsePdfJobService.baixarPdf(id, jobId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nfse-" + id + ".pdf\"")
        .body(pdf);
  }

  @PostMapping("/certificate-unlock")
  public NfseDtos.CertificateUnlockStatusResponse createUnlockSession(
      @RequestBody(required = false) NfseDtos.CertificateUnlockRequest request) {
    validarAcessoFiscalAtual();
    return nfseCertificateUnlockService.createUnlockSession(request);
  }

  @GetMapping("/certificate-unlock")
  public NfseDtos.CertificateUnlockStatusResponse getUnlockStatus() {
    validarAcessoFiscalAtual();
    return nfseCertificateUnlockService.getCurrentStatus();
  }

  @DeleteMapping("/certificate-unlock")
  public ResponseEntity<Void> revokeUnlockSession() {
    validarAcessoFiscalAtual();
    nfseCertificateUnlockService.revokeCurrentSession();
    return ResponseEntity.noContent().build();
  }

  private void validarAcessoFiscalAtual() {
    fiscalAccessService.validarAcessoFiscal(contextoTenant.obterTenantIdOuFalhar());
  }
}
