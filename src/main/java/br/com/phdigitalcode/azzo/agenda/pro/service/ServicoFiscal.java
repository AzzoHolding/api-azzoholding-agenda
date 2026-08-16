package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.phdigitalcode.azzo.agenda.pro.dto.FiscalDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.FiscalDocumentStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.FiscalProviderException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.FiscalStateTransitionException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FiscalInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Espelha {@code modules/fiscal/application/ServicoFiscal.java}.
 *
 * <p>Orquestra o ciclo de vida do documento fiscal por cima do {@link FiscalProvider} (mock ou
 * real): CRUD de invoice, autorizacao junto a SEFAZ (com fluxo de contingencia/erro-final),
 * geracao/consulta/download de DANFE, certificados e apuracao mensal. Toda transicao de status
 * passa por {@link #validarTransicaoComFallback}, que usa {@link FiscalDocumentStateMachine#canReach}
 * — <b>alcancabilidade BFS, nao transicao direta</b>; e assim que o original valida, preservado
 * aqui mesmo parecendo estranho para uma checagem pontual.
 *
 * <p>{@code @ConfigProperty} do Quarkus vira {@link Value} via construtor; {@code JsonWebToken}
 * vira {@link AuthenticatedUser#roleOuNulo()}; o header {@code X-Request-Id} e lido via
 * {@link RequestContextHolder} (mesmo padrao de {@link ContextoTenant#obterTenantIdOuFalhar()});
 * {@code TraceContext.traceId()} do SkyWalking vira {@link CorrelatedLogging#traceId()}.
 */
@Service
public class ServicoFiscal {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoFiscal.class);

  private final ContextoTenant contextoTenant;
  private final FiscalProvider fiscalProvider;
  private final FiscalDocumentStateMachine fiscalDocumentStateMachine;
  private final AuditService auditService;
  private final AuthenticatedUser authenticatedUser;
  private final FiscalPersistenceService fiscalPersistenceService;
  private final FiscalDanfeJobService fiscalDanfeJobService;
  private final FiscalInvoiceRepository fiscalInvoiceRepository;
  private final FiscalInvoiceEventService fiscalInvoiceEventService;
  private final FiscalCertificateService fiscalCertificateService;
  private final FiscalRuleValidationService fiscalRuleValidationService;
  private final FiscalTaxCalculationService fiscalTaxCalculationService;
  private final FiscalXmlBuilderService fiscalXmlBuilderService;
  private final FiscalXmlSignerService fiscalXmlSignerService;
  private final FiscalSefazClient fiscalSefazClient;
  private final int fiscalSeriePadrao;
  private final String fiscalAmbiente;

  public ServicoFiscal(
      ContextoTenant contextoTenant,
      FiscalProvider fiscalProvider,
      FiscalDocumentStateMachine fiscalDocumentStateMachine,
      AuditService auditService,
      AuthenticatedUser authenticatedUser,
      FiscalPersistenceService fiscalPersistenceService,
      FiscalDanfeJobService fiscalDanfeJobService,
      FiscalInvoiceRepository fiscalInvoiceRepository,
      FiscalInvoiceEventService fiscalInvoiceEventService,
      FiscalCertificateService fiscalCertificateService,
      FiscalRuleValidationService fiscalRuleValidationService,
      FiscalTaxCalculationService fiscalTaxCalculationService,
      FiscalXmlBuilderService fiscalXmlBuilderService,
      FiscalXmlSignerService fiscalXmlSignerService,
      FiscalSefazClient fiscalSefazClient,
      @Value("${app.fiscal.serie-padrao:1}") int fiscalSeriePadrao,
      @Value("${app.fiscal.ambiente:HOMOLOGACAO}") String fiscalAmbiente) {
    this.contextoTenant = contextoTenant;
    this.fiscalProvider = fiscalProvider;
    this.fiscalDocumentStateMachine = fiscalDocumentStateMachine;
    this.auditService = auditService;
    this.authenticatedUser = authenticatedUser;
    this.fiscalPersistenceService = fiscalPersistenceService;
    this.fiscalDanfeJobService = fiscalDanfeJobService;
    this.fiscalInvoiceRepository = fiscalInvoiceRepository;
    this.fiscalInvoiceEventService = fiscalInvoiceEventService;
    this.fiscalCertificateService = fiscalCertificateService;
    this.fiscalRuleValidationService = fiscalRuleValidationService;
    this.fiscalTaxCalculationService = fiscalTaxCalculationService;
    this.fiscalXmlBuilderService = fiscalXmlBuilderService;
    this.fiscalXmlSignerService = fiscalXmlSignerService;
    this.fiscalSefazClient = fiscalSefazClient;
    this.fiscalSeriePadrao = fiscalSeriePadrao;
    this.fiscalAmbiente = fiscalAmbiente;
  }

  @Transactional(readOnly = true)
  public FiscalDtos.TaxConfig obterTaxConfig() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.obterTaxConfig(tenantId);
  }

  @Transactional
  public FiscalDtos.TaxConfig atualizarTaxConfig(FiscalDtos.TaxConfig request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.atualizarTaxConfig(tenantId, request);
  }

  @Transactional(readOnly = true)
  public FiscalDtos.InvoiceListResponse listarInvoices(
      String status, String from, String to, Integer page, Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalDtos.InvoiceListResponse response =
        fiscalProvider.listarInvoices(tenantId, status, from, to, page, pageSize);
    if (response != null && response.items != null) {
      response.items.forEach(invoice -> enriquecerNumeracaoFiscal(tenantId, invoice));
    }
    return response;
  }

  @Transactional(readOnly = true)
  public FiscalDtos.Invoice obterInvoice(String id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalDtos.Invoice invoice = fiscalProvider.obterInvoice(tenantId, id);
    enriquecerNumeracaoFiscal(tenantId, invoice);
    return invoice;
  }

  @Transactional
  public FiscalDtos.Invoice criarInvoice(FiscalDtos.Invoice request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (request == null) request = new FiscalDtos.Invoice();

    FiscalDtos.TaxConfig taxConfig = fiscalProvider.obterTaxConfig(tenantId);
    fiscalRuleValidationService.validarCriacao(request, taxConfig);
    FiscalTaxCalculationService.TaxCalculationResult calculation =
        fiscalTaxCalculationService.calcular(request, taxConfig);
    fiscalRuleValidationService.validarTotais(request, calculation);

    // Initial state is always controlled by backend.
    request.status = FiscalDocumentStatus.DRAFT.name();
    FiscalDtos.Invoice created = fiscalProvider.criarInvoice(tenantId, request);
    fiscalPersistenceService.upsertInvoice(tenantId, created);
    validarTransicaoComFallback(
        request.status, created != null ? created.status : null, "criar invoice");
    auditarTransicao(
        tenantId,
        created != null ? created.id : null,
        request.status,
        created != null ? created.status : null,
        "CREATE");
    return created;
  }

  @Transactional
  public FiscalDtos.Invoice atualizarInvoice(String id, FiscalDtos.Invoice request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalDtos.Invoice atual = fiscalProvider.obterInvoice(tenantId, id);
    if (atual == null) {
      throw new IllegalArgumentException("Invoice nao encontrada para atualizacao.");
    }
    if (!"DRAFT".equalsIgnoreCase(atual.status)) {
      throw new IllegalArgumentException("Somente invoices em rascunho podem ser alteradas.");
    }
    if (request == null) request = new FiscalDtos.Invoice();

    FiscalDtos.TaxConfig taxConfig = fiscalProvider.obterTaxConfig(tenantId);
    fiscalRuleValidationService.validarCriacao(request, taxConfig);
    FiscalTaxCalculationService.TaxCalculationResult calculation =
        fiscalTaxCalculationService.calcular(request, taxConfig);
    fiscalRuleValidationService.validarTotais(request, calculation);

    request.id = id;
    request.status = "DRAFT";
    FiscalDtos.Invoice atualizado = fiscalProvider.atualizarInvoice(tenantId, id, request);
    fiscalPersistenceService.upsertInvoice(tenantId, atualizado);
    validarTransicaoComFallback(
        atual != null ? atual.status : null,
        atualizado != null ? atualizado.status : null,
        "atualizar invoice");
    auditarTransicao(
        tenantId,
        id,
        atual != null ? atual.status : null,
        atualizado != null ? atualizado.status : null,
        "UPDATE");
    return atualizado;
  }

  @Transactional
  public FiscalDtos.Invoice cancelarInvoice(String id, FiscalDtos.CancelInvoiceRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalDtos.Invoice atual = fiscalProvider.obterInvoice(tenantId, id);
    validarPreCondicoesCancelamento(atual, request);
    FiscalDtos.Invoice atualizado = fiscalProvider.cancelarInvoice(tenantId, id, request);
    validarTransicaoComFallback(
        atual != null ? atual.status : null,
        atualizado != null ? atualizado.status : null,
        "cancelar invoice");
    auditarTransicao(
        tenantId,
        id,
        atual != null ? atual.status : null,
        atualizado != null ? atualizado.status : null,
        "CANCEL");
    return atualizado;
  }

  @Transactional
  public FiscalDtos.Invoice autorizarInvoice(String id, String certificatePassword) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    fiscalCertificateService.validarSenhaCertificadoAtivo(certificatePassword);
    FiscalDtos.Invoice atual = fiscalProvider.obterInvoice(tenantId, id);
    validarEstruturaMinimaAntesDeAssinar(atual);
    // Garante registro local antes da reserva de numeracao (especialmente em provider mock).
    fiscalPersistenceService.upsertInvoice(tenantId, atual);
    String xml = fiscalXmlBuilderService.buildAndValidate(atual);

    String modelo = resolveModeloFiscal(atual != null ? atual.type : null);
    String numeroReservado =
        fiscalPersistenceService.reservarNumeroFiscal(tenantId, id, modelo, fiscalSeriePadrao, fiscalAmbiente);
    if (atual != null) {
      atual.numeroNf = numeroReservado;
      atual.serieNf = String.valueOf(fiscalSeriePadrao);
    }
    String signedXml = fiscalXmlSignerService.sign(xml, certificatePassword);
    fiscalPersistenceService.salvarXmlAssinado(tenantId, id, signedXml);

    FiscalDtos.Invoice atualizado;
    try {
      atualizado = fiscalSefazClient.autorizarInvoice(tenantId, id);
    } catch (FiscalProviderException ex) {
      LOG.warn(
          CorrelatedLogging.context(
              "fiscal_nfe_authorize_failed", "invoiceId", id, "tenant", tenantId),
          ex);
      if (deveEntrarEmContingencia(atual, ex)) {
        fiscalPersistenceService.atualizarStatusInvoice(
            tenantId, id, FiscalDocumentStatus.CONTINGENCY_PENDING.name());
        registrarEventoTecnicoFalhaPreEnvio(tenantId, id, "CONTINGENCY_PENDING", ex);
      } else {
        fiscalPersistenceService.atualizarStatusInvoice(tenantId, id, "ERROR_FINAL");
        registrarEventoTecnicoFalhaPreEnvio(tenantId, id, "ERROR_FINAL", ex);
        registrarEventoNecessidadeInutilizacao(tenantId, id);
      }
      throw ex;
    }
    validarAutorizacaoComProtocolo(atualizado);
    enriquecerNumeracaoFiscal(tenantId, atualizado);
    fiscalPersistenceService.upsertInvoice(tenantId, atualizado);
    validarTransicaoComFallback(
        atual != null ? atual.status : null,
        atualizado != null ? atualizado.status : null,
        "autorizar invoice");
    auditarTransicao(
        tenantId,
        id,
        atual != null ? atual.status : null,
        atualizado != null ? atualizado.status : null,
        "AUTHORIZE");
    return atualizado;
  }

  public FiscalDtos.Invoice reprocessarAutorizacaoInvoice(String id, String certificatePassword) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    FiscalDtos.Invoice atual = fiscalProvider.obterInvoice(tenantId, id);
    String statusAtual = atual != null ? atual.status : null;
    boolean permitido =
        "ERROR_FINAL".equalsIgnoreCase(statusAtual) || "CONTINGENCY_PENDING".equalsIgnoreCase(statusAtual);
    if (!permitido) {
      throw new IllegalArgumentException(
          "Reprocessamento manual permitido apenas para invoices em ERROR_FINAL ou CONTINGENCY_PENDING");
    }
    return autorizarInvoice(id, certificatePassword);
  }

  @Transactional(readOnly = true)
  public byte[] gerarPdfInvoice(String id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.gerarPdfInvoice(tenantId, id);
  }

  public FiscalDtos.DanfeJobResponse solicitarGeracaoDanfe(String invoiceId) {
    return fiscalDanfeJobService.solicitarGeracao(invoiceId);
  }

  public FiscalDtos.DanfeJobResponse consultarJobDanfe(String invoiceId, String jobId) {
    return fiscalDanfeJobService.consultarJob(invoiceId, jobId);
  }

  public byte[] baixarDanfeJob(String invoiceId, String jobId) {
    return fiscalDanfeJobService.baixarDanfe(invoiceId, jobId);
  }

  public List<FiscalDtos.CertificateResponse> listarCertificados() {
    return fiscalCertificateService.listar();
  }

  public FiscalDtos.CertificateResponse salvarCertificado(FiscalDtos.CertificateUpsertRequest request) {
    return fiscalCertificateService.salvar(request);
  }

  public FiscalDtos.CertificateResponse ativarCertificado(String certificateId) {
    return fiscalCertificateService.ativar(certificateId);
  }

  public void removerCertificado(String certificateId) {
    fiscalCertificateService.remover(certificateId);
  }

  @Transactional(readOnly = true)
  public FiscalDtos.ApuracaoMensal obterApuracaoAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.obterApuracaoAtual(tenantId);
  }

  @Transactional(readOnly = true)
  public FiscalDtos.ApuracaoMensal obterApuracao(int ano, int mes) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.obterApuracao(tenantId, ano, mes);
  }

  @Transactional
  public FiscalDtos.ApuracaoMensal recalcularApuracao(int ano, int mes) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.recalcularApuracao(tenantId, ano, mes);
  }

  @Transactional(readOnly = true)
  public List<FiscalDtos.ApuracaoResumo> historico(int limite) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.historico(tenantId, limite);
  }

  @Transactional(readOnly = true)
  public FiscalDtos.ResumoAnual resumoAnual(int ano) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return fiscalProvider.resumoAnual(tenantId, ano);
  }

  private void validarTransicaoComFallback(String fromRaw, String toRaw, String operacao) {
    var from = FiscalDocumentStatus.tryParse(fromRaw);
    var to = FiscalDocumentStatus.tryParse(toRaw);

    if (from.isEmpty() || to.isEmpty()) {
      LOG.warn(
          CorrelatedLogging.context(
              "Fiscal status fora da maquina de estados, validacao ignorada",
              "operacao",
              operacao,
              "from",
              fromRaw,
              "to",
              toRaw));
      return;
    }

    if (!fiscalDocumentStateMachine.canReach(from.get(), to.get())) {
      throw new FiscalStateTransitionException(
          String.format("Transicao fiscal invalida: %s -> %s (%s)", from.get(), to.get(), operacao));
    }
  }

  private void enriquecerNumeracaoFiscal(UUID tenantId, FiscalDtos.Invoice invoice) {
    if (tenantId == null || invoice == null || invoice.id == null || invoice.id.isBlank()) return;
    fiscalInvoiceRepository
        .findByTenantAndExternalInvoiceId(tenantId, invoice.id)
        .ifPresent(
            entity -> {
              if ((invoice.numeroNf == null || invoice.numeroNf.isBlank())
                  && entity.getNumeroNf() != null
                  && !entity.getNumeroNf().isBlank()) {
                invoice.numeroNf = entity.getNumeroNf();
              }
              if ((invoice.serieNf == null || invoice.serieNf.isBlank())
                  && entity.getSerieNf() != null
                  && !entity.getSerieNf().isBlank()) {
                invoice.serieNf = entity.getSerieNf();
              }
            });
  }

  private void auditarTransicao(
      UUID tenantId, String invoiceId, String fromStatus, String toStatus, String operation) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorRole = authenticatedUser.roleOuNulo();
      command.module = AuditConstants.Module.FISCAL;
      command.action = "STATE_TRANSITION_" + operation;
      command.entityType = "FISCAL_INVOICE";
      command.entityId = invoiceId;
      command.requestId = resolveRequestId();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      Map<String, Object> before = new HashMap<>();
      before.put("status", fromStatus);
      Map<String, Object> after = new HashMap<>();
      after.put("status", toStatus);
      command.before = before;
      command.after = after;
      command.metadata = Map.of("operation", operation);
      auditService.recordSuccess(command);

      fiscalInvoiceRepository
          .findByTenantAndExternalInvoiceId(tenantId, invoiceId)
          .ifPresent(
              invoice ->
                  fiscalInvoiceEventService.registrarEvento(
                      tenantId,
                      invoice.getId(),
                      "STATE_TRANSITION",
                      "SUCCESS",
                      null,
                      fromStatus + " -> " + toStatus));
    } catch (Exception e) {
      LOG.warn(
          CorrelatedLogging.context("Falha ao auditar transicao fiscal", "invoiceId", invoiceId), e);
    }
  }

  private String resolveRequestId() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) return null;
    HttpServletRequest request = attributes.getRequest();
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) requestId = request.getHeader("x-request-id");
    return requestId;
  }

  private String resolveModeloFiscal(String type) {
    if (type == null || type.isBlank()) return "55";
    String normalized = type.trim().toUpperCase(Locale.ROOT);
    if ("NFCE".equals(normalized) || "65".equals(normalized)) return "65";
    return "55";
  }

  private void validarAutorizacaoComProtocolo(FiscalDtos.Invoice invoice) {
    if (invoice == null) return;
    var status = FiscalDocumentStatus.tryParse(invoice.status);
    if (status.isPresent()
        && status.get() == FiscalDocumentStatus.AUTHORIZED
        && (invoice.authorizationProtocol == null || invoice.authorizationProtocol.isBlank())) {
      throw new FiscalStateTransitionException("Documento fiscal nao pode ficar AUTHORIZED sem protocolo SEFAZ");
    }
  }

  private void validarEstruturaMinimaAntesDeAssinar(FiscalDtos.Invoice invoice) {
    if (invoice == null) {
      throw new IllegalArgumentException("Invoice fiscal nao encontrada para autorizacao.");
    }
    if (invoice.customer == null) {
      throw new IllegalArgumentException("Invoice sem destinatario/cliente para autorizacao.");
    }
    if (invoice.items == null || invoice.items.isEmpty()) {
      throw new IllegalArgumentException("Invoice sem itens para autorizacao.");
    }
    if (invoice.type == null || invoice.type.isBlank()) {
      throw new IllegalArgumentException("Invoice sem modelo fiscal definido para autorizacao.");
    }
  }

  private void validarPreCondicoesCancelamento(
      FiscalDtos.Invoice atual, FiscalDtos.CancelInvoiceRequest request) {
    String motivo = request != null ? request.reason : null;
    if (motivo == null || motivo.isBlank()) {
      throw new IllegalArgumentException("Motivo de cancelamento obrigatorio.");
    }

    if (atual == null || atual.status == null || atual.status.isBlank()) {
      throw new FiscalStateTransitionException("Cancelamento permitido apenas para nota em status AUTHORIZED.");
    }

    var statusAtual = FiscalDocumentStatus.tryParse(atual.status);
    if (statusAtual.isEmpty()) {
      throw new FiscalStateTransitionException("Status fiscal invalido para cancelamento: " + atual.status);
    }

    boolean permitido =
        statusAtual.get() == FiscalDocumentStatus.AUTHORIZED
            || statusAtual.get() == FiscalDocumentStatus.CANCEL_PENDING;
    if (!permitido) {
      throw new FiscalStateTransitionException(
          "Cancelamento permitido apenas para nota em status AUTHORIZED ou CANCEL_PENDING.");
    }
  }

  private boolean deveEntrarEmContingencia(FiscalDtos.Invoice atual, FiscalProviderException ex) {
    if (atual == null || ex == null) return false;
    String modelo = resolveModeloFiscal(atual.type);
    if (!"65".equals(modelo)) return false; // MVP: apenas NFC-e offline.
    int status = ex.getStatusCode();
    return status == 408 || status == 429 || status == 502 || status == 503 || status == 504;
  }

  private void registrarEventoTecnicoFalhaPreEnvio(
      UUID tenantId, String externalInvoiceId, String statusFinal, FiscalProviderException ex) {
    try {
      fiscalInvoiceRepository
          .findByTenantAndExternalInvoiceId(tenantId, externalInvoiceId)
          .ifPresent(
              invoice ->
                  fiscalInvoiceEventService.registrarEvento(
                      tenantId,
                      invoice.getId(),
                      "AUTHORIZATION_PRE_SUBMISSION_FAILURE",
                      statusFinal,
                      String.valueOf(ex.getStatusCode()),
                      ex.getMessage()));
    } catch (Exception ignored) {
      LOG.warn(
          CorrelatedLogging.context(
              "fiscal_event_registration_failed",
              "invoiceId",
              externalInvoiceId,
              "statusFinal",
              statusFinal));
    }
  }

  private void registrarEventoNecessidadeInutilizacao(UUID tenantId, String externalInvoiceId) {
    try {
      String numeroReservado = fiscalPersistenceService.obterNumeroFiscalReservado(tenantId, externalInvoiceId);
      if (numeroReservado == null || numeroReservado.isBlank()) return;
      fiscalInvoiceRepository
          .findByTenantAndExternalInvoiceId(tenantId, externalInvoiceId)
          .ifPresent(
              invoice ->
                  fiscalInvoiceEventService.registrarEvento(
                      tenantId,
                      invoice.getId(),
                      "FISCAL_NUMBER_INUTILIZATION_REQUIRED",
                      "PENDING_MANUAL_ACTION",
                      "INUTILIZATION_REQUIRED",
                      "Numero fiscal reservado sem envio concluido. Iniciar inutilizacao formal do numero "
                          + numeroReservado));
    } catch (Exception ignored) {
      LOG.warn(
          CorrelatedLogging.context("fiscal_inutilizacao_event_failed", "invoiceId", externalInvoiceId));
    }
  }
}
