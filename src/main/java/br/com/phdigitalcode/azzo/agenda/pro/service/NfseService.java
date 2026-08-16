package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.servlet.http.HttpServletRequest;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEventEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseProviderCapabilitiesEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAccountingExportFormat;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseStateTransitionException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseProviderCapabilitiesRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Porte de {@code modules/nfse/application/NfseService.java} (1313L no original, o maior arquivo
 * do modulo) — Fronteira 6 (ver {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/26/27). Orquestrador
 * central de {@code nfse}: CRUD de invoice/rascunho, {@link #autorizar}, {@link #cancelar},
 * exportacao contabil (CSV/XLSX/ZIP via Apache POI) e lookup de tomador via CNPJ.
 *
 * <p><b>Nao reimplementa</b> config/provider-capabilities: essas fatias de CRUD ja foram extraidas
 * como servicos proprios na Fronteira 2 ({@link NfseConfigService}, {@link
 * NfseProviderCapabilitiesService}) — o controller (Fronteira 6) injeta os dois direto, sem passar
 * por aqui, exatamente como o {@code NfseResource} original chamava os metodos correspondentes do
 * {@code NfseService} monolitico. {@link #resolveProvedor} e reusado via {@link NfseConfigService}
 * em vez de duplicado aqui.
 *
 * <p>Assimetrias e achados do original preservados de proposito (Etapa 25, nao "consertar"):
 *
 * <ul>
 *   <li><b>{@code CANCEL_REJECTED} inalcancavel na pratica</b>: {@link #cancelar} so persiste
 *       {@code CANCELLED} em sucesso; se {@code provider.cancel(...)} lanca excecao, o rollback do
 *       {@code @Transactional} devolve a invoice para {@code AUTHORIZED} — a transicao para {@code
 *       CANCEL_REJECTED} nunca e de fato gravada. Nao ha {@code try/catch} ao redor da chamada do
 *       provider em {@link #cancelar}, diferente de {@link #autorizar} (que captura para logar
 *       metricas antes de relancar) — essa assimetria tambem e do original.
 *   <li><b>Providers de teste (TEST_OK/MOCK_FAIL) embutidos no fluxo de producao</b>: {@link
 *       #autorizar} tem um caminho paralelo ({@code isTestProvider}/{@code
 *       app.nfse.test-providers.enabled}) que convive com o fluxo real via {@link
 *       NfseProviderRouterService}. Preservado 1:1 — nao simplificado.
 *   <li><b>Resolucao de URL assimetrica entre providers</b> (ABRASF tem 3 fallbacks, SEFIN
 *       Nacional so 1) vive dentro dos adapters (Fronteira 5), nao aqui.
 * </ul>
 */
@Service
public class NfseService {

  private static final Logger LOG = LoggerFactory.getLogger(NfseService.class);

  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;
  private final MeterRegistry meterRegistry;
  private final NfseConfigRepository nfseConfigRepository;
  private final NfseInvoiceRepository nfseInvoiceRepository;
  private final NfseInvoiceEventRepository nfseInvoiceEventRepository;
  private final NfseInvoiceItemRepository nfseInvoiceItemRepository;
  private final NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository;
  private final FiscalCertificateService fiscalCertificateService;
  private final NfseCertificateUnlockService nfseCertificateUnlockService;
  private final NfseProviderRouterService nfseProviderRouterService;
  private final NfseFiscalStateMachine nfseFiscalStateMachine;
  private final NfseXmlBuilderService nfseXmlBuilderService;
  private final NfseXmlSignerService nfseXmlSignerService;
  private final EncryptionService encryptionService;
  private final BrasilApiCnpjClient brasilApiCnpjClient;
  private final NfseConfigService nfseConfigService;
  private final boolean tomadorCnpjLookupEnabled;
  private final boolean testProvidersEnabled;

  public NfseService(
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser,
      MeterRegistry meterRegistry,
      NfseConfigRepository nfseConfigRepository,
      NfseInvoiceRepository nfseInvoiceRepository,
      NfseInvoiceEventRepository nfseInvoiceEventRepository,
      NfseInvoiceItemRepository nfseInvoiceItemRepository,
      NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository,
      FiscalCertificateService fiscalCertificateService,
      NfseCertificateUnlockService nfseCertificateUnlockService,
      NfseProviderRouterService nfseProviderRouterService,
      NfseFiscalStateMachine nfseFiscalStateMachine,
      NfseXmlBuilderService nfseXmlBuilderService,
      NfseXmlSignerService nfseXmlSignerService,
      EncryptionService encryptionService,
      BrasilApiCnpjClient brasilApiCnpjClient,
      NfseConfigService nfseConfigService,
      @Value("${app.nfse.tomador.cnpj-lookup.enabled:true}") boolean tomadorCnpjLookupEnabled,
      @Value("${app.nfse.test-providers.enabled:false}") boolean testProvidersEnabled) {
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
    this.meterRegistry = meterRegistry;
    this.nfseConfigRepository = nfseConfigRepository;
    this.nfseInvoiceRepository = nfseInvoiceRepository;
    this.nfseInvoiceEventRepository = nfseInvoiceEventRepository;
    this.nfseInvoiceItemRepository = nfseInvoiceItemRepository;
    this.nfseProviderCapabilitiesRepository = nfseProviderCapabilitiesRepository;
    this.fiscalCertificateService = fiscalCertificateService;
    this.nfseCertificateUnlockService = nfseCertificateUnlockService;
    this.nfseProviderRouterService = nfseProviderRouterService;
    this.nfseFiscalStateMachine = nfseFiscalStateMachine;
    this.nfseXmlBuilderService = nfseXmlBuilderService;
    this.nfseXmlSignerService = nfseXmlSignerService;
    this.encryptionService = encryptionService;
    this.brasilApiCnpjClient = brasilApiCnpjClient;
    this.nfseConfigService = nfseConfigService;
    this.tomadorCnpjLookupEnabled = tomadorCnpjLookupEnabled;
    this.testProvidersEnabled = testProvidersEnabled;
  }

  @Transactional
  public NfseDtos.Invoice criarRascunho(NfseDtos.Invoice request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (request == null) throw new IllegalArgumentException("Payload de NFS-e obrigatorio.");
    NfseAmbiente ambiente = parseAmbiente(request.ambiente);
    NfseConfigEntity config = resolveRequiredConfigForInvoice(tenantId, ambiente, request.municipioCodigoIbge);
    String municipioCodigoIbge = resolveMunicipioCodigoIbge(config, request.municipioCodigoIbge);
    String provedor = nfseConfigService.resolveProvedor(config.getProvedor(), municipioCodigoIbge);
    validarInvoiceRequest(request, municipioCodigoIbge, provedor);

    NfseInvoiceEntity entity = new NfseInvoiceEntity();
    entity.setTenantId(tenantId);
    entity.setAppointmentId(parseUuidOrNull(request.appointmentId));
    entity.setCustomerType(parseCustomerType(request.customer != null ? request.customer.type : null));
    entity.setCustomerDocument(normalizeOrNull(request.customer != null ? request.customer.document : null));
    entity.setCustomerCountryCode(normalizeOrNull(request.customer != null ? request.customer.countryCode : null));
    entity.setCustomerDocumentType(normalizeOrNull(request.customer != null ? request.customer.documentType : null));
    entity.setCustomerName(normalize(request.customer != null ? request.customer.name : null));
    entity.setCustomerEmail(normalizeOrNull(request.customer != null ? request.customer.email : null));
    entity.setCustomerPhone(normalizeOrNull(request.customer != null ? request.customer.phone : null));
    entity.setFiscalStatus(NfseFiscalStatus.DRAFT);
    entity.setOperationalStatus(null);
    entity.setMunicipioCodigoIbge(municipioCodigoIbge);
    entity.setProvedor(provedor);
    entity.setAmbiente(ambiente);
    entity.setSerieRps(normalize(request.serieRps));
    entity.setNumeroRps(
        (request.numeroRps != null && request.numeroRps > 0)
            ? request.numeroRps
            : nfseInvoiceRepository.nextRpsNumber(tenantId, municipioCodigoIbge, entity.getSerieRps(), ambiente.name()));
    entity.setNumeroNfse(null);
    entity.setCodigoVerificacao(null);
    entity.setChaveAcessoNfse(null);
    entity.setProtocolo(null);
    entity.setDataCompetencia(parseDateOrToday(request.dataCompetencia));
    entity.setDataEmissao(null);
    entity.setNaturezaOperacao(normalize(request.naturezaOperacao));
    entity.setItemListaServico(normalize(request.itemListaServico));
    entity.setCodigoTributacaoMunicipio(normalizeOrNull(request.codigoTributacaoMunicipio));
    entity.setNationalTaxCode(normalizeOrNull(request.nationalTaxCode));
    entity.setNbsCode(normalizeOrNull(request.nbsCode));
    entity.setLocalPrestacaoCodigoIbge(normalizeOrNull(request.localPrestacaoCodigoIbge));
    entity.setValorServicos(nvl(request.valorServicos));
    entity.setValorDeducoes(nvl(request.valorDeducoes));
    entity.setValorIss(nvl(request.valorIss));
    entity.setAliquotaIss(nvl(request.aliquotaIss));
    entity.setIssRetido(request.issRetido);
    entity.setNotes(normalizeOrNull(request.notes));

    nfseInvoiceRepository.save(entity);
    persistirItens(tenantId, entity.getId(), request.items);
    return toInvoiceDtoFromRequest(entity, request.items);
  }

  @Transactional
  public NfseDtos.Invoice atualizarRascunho(String invoiceId, NfseDtos.Invoice request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID id = parseUuid(invoiceId);
    NfseInvoiceEntity entity =
        nfseInvoiceRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("NFS-e nao encontrada.", 404));
    if (entity.getFiscalStatus() != NfseFiscalStatus.DRAFT) {
      throw new NfseStateTransitionException("Somente rascunhos podem ser alterados.");
    }
    if (request == null) throw new IllegalArgumentException("Payload de NFS-e obrigatorio.");
    NfseAmbiente ambiente = parseAmbiente(request.ambiente);
    NfseConfigEntity config = resolveRequiredConfigForInvoice(tenantId, ambiente, request.municipioCodigoIbge);
    String municipioCodigoIbge = resolveMunicipioCodigoIbge(config, request.municipioCodigoIbge);
    String provedor = nfseConfigService.resolveProvedor(config.getProvedor(), municipioCodigoIbge);
    validarInvoiceRequest(request, municipioCodigoIbge, provedor);

    entity.setCustomerType(parseCustomerType(request.customer != null ? request.customer.type : null));
    entity.setCustomerDocument(normalizeOrNull(request.customer != null ? request.customer.document : null));
    entity.setCustomerCountryCode(normalizeOrNull(request.customer != null ? request.customer.countryCode : null));
    entity.setCustomerDocumentType(normalizeOrNull(request.customer != null ? request.customer.documentType : null));
    entity.setCustomerName(normalize(request.customer != null ? request.customer.name : null));
    entity.setCustomerEmail(normalizeOrNull(request.customer != null ? request.customer.email : null));
    entity.setCustomerPhone(normalizeOrNull(request.customer != null ? request.customer.phone : null));
    entity.setMunicipioCodigoIbge(municipioCodigoIbge);
    entity.setProvedor(provedor);
    entity.setAmbiente(ambiente);
    entity.setNumeroRps(request.numeroRps);
    entity.setSerieRps(normalize(request.serieRps));
    entity.setDataCompetencia(parseDateOrToday(request.dataCompetencia));
    entity.setNaturezaOperacao(normalize(request.naturezaOperacao));
    entity.setItemListaServico(normalize(request.itemListaServico));
    entity.setCodigoTributacaoMunicipio(normalizeOrNull(request.codigoTributacaoMunicipio));
    entity.setNationalTaxCode(normalizeOrNull(request.nationalTaxCode));
    entity.setNbsCode(normalizeOrNull(request.nbsCode));
    entity.setLocalPrestacaoCodigoIbge(normalizeOrNull(request.localPrestacaoCodigoIbge));
    entity.setValorServicos(nvl(request.valorServicos));
    entity.setValorDeducoes(nvl(request.valorDeducoes));
    entity.setValorIss(nvl(request.valorIss));
    entity.setAliquotaIss(nvl(request.aliquotaIss));
    entity.setIssRetido(request.issRetido);
    entity.setNotes(normalizeOrNull(request.notes));

    nfseInvoiceItemRepository.deleteByTenantAndInvoice(tenantId, entity.getId());
    persistirItens(tenantId, entity.getId(), request.items);
    return toInvoiceDtoFromRequest(entity, request.items);
  }

  @Transactional(readOnly = true)
  public NfseDtos.Invoice obterInvoice(String invoiceId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID id = parseUuid(invoiceId);
    NfseInvoiceEntity entity =
        nfseInvoiceRepository
            .findByTenantAndId(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("NFS-e nao encontrada.", 404));
    List<NfseInvoiceItemEntity> items = nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, id);
    return toInvoiceDto(entity, items);
  }

  @Transactional
  public NfseDtos.Invoice autorizar(String invoiceId, NfseDtos.AuthorizeRequest request) {
    Instant startedAt = Instant.now();
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID id = parseUuid(invoiceId);
    String correlationId = resolveCorrelationId();
    String certificatePassword = request != null ? request.certificatePassword : null;
    if (isBlank(certificatePassword) && request != null && !isBlank(request.unlockTokenId)) {
      certificatePassword = nfseCertificateUnlockService.resolvePasswordFromToken(request.unlockTokenId);
    }
    if (isBlank(certificatePassword)) {
      throw new IllegalArgumentException(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
    }
    fiscalCertificateService.validarSenhaCertificadoAtivo(certificatePassword);

    NfseInvoiceEntity entity =
        nfseInvoiceRepository
            .findByTenantAndIdForUpdate(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("NFS-e nao encontrada.", 404));
    if (entity.getFiscalStatus() != NfseFiscalStatus.DRAFT
        && entity.getFiscalStatus() != NfseFiscalStatus.REJECTED) {
      throw new NfseStateTransitionException("Estado invalido para autorizacao.");
    }

    aplicarProvedorSelecionado(entity, request != null ? request.provedor : null);

    if ("MOCK_NACIONAL".equalsIgnoreCase(entity.getProvedor())) {
      throw new IllegalArgumentException("Provedor MOCK_NACIONAL nao permitido nesta operacao.");
    }
    if (entity.getFiscalStatus() == NfseFiscalStatus.REJECTED) {
      transicionarFiscalStatus(entity, NfseFiscalStatus.DRAFT, tenantId, id, "RETRY_RESET");
    }

    if (isTestProvider(entity.getProvedor())) {
      if ("MOCK_FAIL".equalsIgnoreCase(entity.getProvedor())) {
        throw new IllegalStateException("Falha simulada do provedor NFS-e.");
      }
      transicionarFiscalStatus(entity, NfseFiscalStatus.READY_TO_SEND, tenantId, id, "READY_TO_SEND");
      entity.setXmlEnvioEnc(asEncryptedBytes("<nfse-test-provider/>"));
      transicionarFiscalStatus(entity, NfseFiscalStatus.SIGNED, tenantId, id, "SIGNED");
      transicionarFiscalStatus(entity, NfseFiscalStatus.SUBMITTED, tenantId, id, "SUBMITTED");
      NfseProviderAdapter.AuthorizationResult authorizationResult =
          authorizeWithProvider(entity, certificatePassword);
      return finalizarAutorizacao(entity, tenantId, id, correlationId, startedAt, authorizationResult);
    }

    transicionarFiscalStatus(entity, NfseFiscalStatus.READY_TO_SEND, tenantId, id, "READY_TO_SEND");
    String xmlAutorizacao = nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity);
    String xmlAssinado = nfseXmlSignerService.sign(xmlAutorizacao, certificatePassword);
    entity.setXmlEnvioEnc(asEncryptedBytes(xmlAssinado));
    transicionarFiscalStatus(entity, NfseFiscalStatus.SIGNED, tenantId, id, "SIGNED");
    transicionarFiscalStatus(entity, NfseFiscalStatus.SUBMITTED, tenantId, id, "SUBMITTED");

    NfseProviderAdapter.AuthorizationResult authorizationResult;
    try {
      authorizationResult = authorizeWithProvider(entity, certificatePassword);
    } catch (RuntimeException ex) {
      registrarMetricasAutorizacao(false, entity, startedAt, "NA");
      LOG.warn(
          "nfse_authorize_error correlationId={} tenant_id={} invoice_id={} ambiente={} municipio={} provedor={}",
          correlationId,
          tenantId,
          id,
          entity.getAmbiente(),
          entity.getMunicipioCodigoIbge(),
          entity.getProvedor(),
          ex);
      throw ex;
    }

    return finalizarAutorizacao(entity, tenantId, id, correlationId, startedAt, authorizationResult);
  }

  private NfseDtos.Invoice finalizarAutorizacao(
      NfseInvoiceEntity entity,
      UUID tenantId,
      UUID id,
      String correlationId,
      Instant startedAt,
      NfseProviderAdapter.AuthorizationResult authorizationResult) {
    if (isPendingAuthorizationResult(authorizationResult)) {
      transicionarFiscalStatus(entity, NfseFiscalStatus.PENDING, tenantId, id, "PENDING");
      entity.setOperationalStatus(NfseOperationalStatus.WAITING_PROVIDER);
      entity.setProtocolo(authorizationResult.protocolo());
      entity.setNumeroNfse(authorizationResult.numeroNfse());
      entity.setCodigoVerificacao(authorizationResult.codigoVerificacao());
      entity.setChaveAcessoNfse(authorizationResult.chaveAcessoNfse());
      entity.setDataEmissao(null);
      String retornoXml = buildAuthorizationReturnXml(entity, authorizationResult);
      entity.setXmlRetornoEnc(asEncryptedBytes(retornoXml));
      registrarEvento(
          tenantId,
          id,
          "AUTHORIZE_PENDING",
          "PENDING",
          authorizationResult.providerStatusCode(),
          authorizationResult.providerStatusMessage(),
          retornoXml);
      registrarMetricasAutorizacao(true, entity, startedAt, authorizationResult.providerStatusCode());
      LOG.info(
          "nfse_authorize_pending correlationId={} tenant_id={} invoice_id={} ambiente={} municipio={} provedor={} protocol={}",
          correlationId,
          tenantId,
          id,
          entity.getAmbiente(),
          entity.getMunicipioCodigoIbge(),
          entity.getProvedor(),
          entity.getProtocolo());
    } else {
      transicionarFiscalStatus(entity, NfseFiscalStatus.AUTHORIZED, tenantId, id, "AUTHORIZED");
      entity.setOperationalStatus(null);
      entity.setNumeroNfse(authorizationResult.numeroNfse());
      entity.setProtocolo(authorizationResult.protocolo());
      entity.setCodigoVerificacao(authorizationResult.codigoVerificacao());
      entity.setChaveAcessoNfse(authorizationResult.chaveAcessoNfse());
      entity.setDataEmissao(Instant.now());
      String retornoXml = buildAuthorizationReturnXml(entity, authorizationResult);
      entity.setXmlRetornoEnc(asEncryptedBytes(retornoXml));
      registrarEvento(
          tenantId,
          id,
          "AUTHORIZE_SUCCESS",
          "AUTHORIZED",
          authorizationResult.providerStatusCode(),
          authorizationResult.providerStatusMessage(),
          retornoXml);
      registrarMetricasAutorizacao(true, entity, startedAt, authorizationResult.providerStatusCode());
      LOG.info(
          "nfse_authorize_success correlationId={} tenant_id={} invoice_id={} ambiente={} municipio={} provedor={} fiscal_status={}",
          correlationId,
          tenantId,
          id,
          entity.getAmbiente(),
          entity.getMunicipioCodigoIbge(),
          entity.getProvedor(),
          entity.getFiscalStatus());
    }

    List<NfseInvoiceItemEntity> items = nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, id);
    return toInvoiceDto(entity, items);
  }

  /**
   * Aplica o provedor escolhido pelo usuario no momento da autorizacao. Vazio mantem o provedor
   * resolvido na criacao do rascunho. So aceita se o provedor estiver disponivel para a empresa:
   * capability cadastrada para o municipio do documento (provedores de teste exigem a flag {@code
   * app.nfse.test-providers.enabled}; {@code MOCK_NACIONAL} nunca).
   */
  void aplicarProvedorSelecionado(NfseInvoiceEntity entity, String provedorSolicitado) {
    if (isBlank(provedorSolicitado)) return;
    String provedor = provedorSolicitado.trim();
    if (provedor.equalsIgnoreCase(entity.getProvedor())) return;
    if ("MOCK_NACIONAL".equalsIgnoreCase(provedor)) {
      throw new IllegalArgumentException("NFSE_PROVIDER_NOT_ALLOWED");
    }
    if (isTestProvider(provedor)) {
      entity.setProvedor(provedor.toUpperCase(Locale.ROOT));
      return;
    }
    NfseProviderCapabilitiesEntity capability =
        nfseProviderCapabilitiesRepository
            .findByMunicipioProvedor(entity.getMunicipioCodigoIbge(), provedor)
            .orElseThrow(() -> new IllegalArgumentException("NFSE_PROVIDER_NOT_AVAILABLE"));
    entity.setProvedor(capability.getProvedor());
  }

  private boolean isTestProvider(String provedor) {
    return testProvidersEnabled
        && provedor != null
        && ("TEST_OK".equalsIgnoreCase(provedor) || "MOCK_FAIL".equalsIgnoreCase(provedor));
  }

  private String buildAuthorizationReturnXml(
      NfseInvoiceEntity entity, NfseProviderAdapter.AuthorizationResult authorizationResult) {
    if (isTestProvider(entity != null ? entity.getProvedor() : null)) {
      return "<nfse-test-provider-retorno status=\""
          + authorizationResult.providerStatusCode()
          + "\" message=\""
          + authorizationResult.providerStatusMessage()
          + "\"/>";
    }
    return nfseXmlBuilderService.buildAuthorizationReturnXml(
        entity, authorizationResult.providerStatusCode(), authorizationResult.providerStatusMessage());
  }

  private NfseProviderAdapter.AuthorizationResult authorizeWithProvider(
      NfseInvoiceEntity entity, String certificatePassword) {
    if (testProvidersEnabled && entity != null && entity.getProvedor() != null) {
      if ("TEST_OK".equalsIgnoreCase(entity.getProvedor())) {
        String numeroNfse = entity.getNumeroRps() != null ? String.valueOf(entity.getNumeroRps()) : "1";
        return new NfseProviderAdapter.AuthorizationResult(
            "100", "Autorizado (test provider)", numeroNfse, "PROTO-" + UUID.randomUUID(), "CV-TEST", null);
      }
      if ("MOCK_FAIL".equalsIgnoreCase(entity.getProvedor())) {
        throw new IllegalStateException("Falha simulada do provedor NFS-e.");
      }
    }

    NfseProviderAdapter provider = nfseProviderRouterService.resolve(entity.getProvedor());
    return provider.authorize(entity, certificatePassword);
  }

  @Transactional
  public NfseDtos.Invoice cancelar(String invoiceId, NfseDtos.CancelRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID id = parseUuid(invoiceId);
    if (request == null || isBlank(request.reason)) {
      throw new IllegalArgumentException("Motivo do cancelamento obrigatorio.");
    }

    NfseInvoiceEntity entity =
        nfseInvoiceRepository
            .findByTenantAndIdForUpdate(tenantId, id)
            .orElseThrow(() -> new ApiClientErrorException("NFS-e nao encontrada.", 404));
    if (entity.getFiscalStatus() != NfseFiscalStatus.AUTHORIZED) {
      throw new NfseStateTransitionException("Estado invalido para cancelamento.");
    }

    transicionarFiscalStatus(entity, NfseFiscalStatus.CANCEL_PENDING, tenantId, id, "CANCEL_PENDING");

    NfseProviderAdapter provider = nfseProviderRouterService.resolve(entity.getProvedor());
    NfseProviderAdapter.CancellationResult cancelResult =
        provider.cancel(entity, request.reason, request.certificatePassword);
    transicionarFiscalStatus(entity, NfseFiscalStatus.CANCELLED, tenantId, id, "CANCELLED");
    entity.setXmlRetornoEnc(
        asEncryptedBytes(
            nfseXmlBuilderService.buildCancelReturnXml(
                entity, cancelResult.providerStatusCode(), cancelResult.providerStatusMessage())));
    registrarEvento(
        tenantId,
        id,
        "CANCEL_SUCCESS",
        "CANCELLED",
        cancelResult.providerStatusCode(),
        cancelResult.providerStatusMessage(),
        null);

    List<NfseInvoiceItemEntity> items = nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, id);
    return toInvoiceDto(entity, items);
  }

  @Transactional(readOnly = true)
  public NfseDtos.InvoiceListResponse listarInvoices(String status, Integer page, Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int pg = page == null || page < 1 ? 1 : page;
    int ps = pageSize == null || pageSize < 1 ? 20 : pageSize;

    NfseFiscalStatus fiscalStatus = status == null || status.isBlank() ? null : parseFiscalStatus(status);
    Page<NfseInvoiceEntity> pageResult =
        nfseInvoiceRepository.pageByTenantAndOptionalStatus(tenantId, fiscalStatus, pg, ps);

    NfseDtos.InvoiceListResponse resp = new NfseDtos.InvoiceListResponse();
    resp.items = pageResult.getContent().stream().map(this::toInvoiceDtoWithoutItems).toList();
    resp.total = pageResult.getTotalElements();
    resp.page = pg;
    resp.pageSize = ps;
    return resp;
  }

  @Transactional(readOnly = true)
  public NfseDtos.TomadorLookupResponse consultarTomadorPorCnpj(String cnpjRaw) {
    if (!tomadorCnpjLookupEnabled) {
      throw new IllegalArgumentException("NFSE_TOMADOR_LOOKUP_DISABLED");
    }
    String cnpj = digitsOnly(cnpjRaw);
    if (cnpj.length() != 14) {
      throw new IllegalArgumentException("NFSE_TOMADOR_CNPJ_INVALID");
    }

    BrasilApiCnpjClient.BrasilApiCnpjResponse response;
    try {
      response = brasilApiCnpjClient.lookup(cnpj);
    } catch (Exception ex) {
      LOG.warn("nfse_tomador_lookup_failed cnpj_length={}", cnpj.length(), ex);
      throw new IllegalArgumentException("NFSE_TOMADOR_LOOKUP_UNAVAILABLE");
    }
    if (response == null || isBlank(response.razao_social)) {
      throw new IllegalArgumentException("NFSE_TOMADOR_NOT_FOUND");
    }

    NfseDtos.TomadorLookupResponse dto = new NfseDtos.TomadorLookupResponse();
    dto.document = cnpj;
    dto.name = normalizeOrNull(response.razao_social);
    dto.tradeName = normalizeOrNull(response.nome_fantasia);
    dto.email = normalizeOrNull(response.email);
    dto.phone = normalizeOrNull(response.ddd_telefone_1);
    dto.status = normalizeOrNull(response.descricao_situacao_cadastral);
    dto.active = "ATIVA".equalsIgnoreCase(dto.status);
    dto.source = "BRASILAPI";

    NfseDtos.TomadorLookupAddress address = new NfseDtos.TomadorLookupAddress();
    String tipoLogradouro = normalizeOrNull(response.descricao_tipo_de_logradouro);
    String logradouro = normalizeOrNull(response.logradouro);
    if (!isBlank(tipoLogradouro) && !isBlank(logradouro)) {
      address.street = tipoLogradouro + " " + logradouro;
    } else {
      address.street = normalizeOrNull(logradouro);
    }
    address.number = normalizeOrNull(response.numero);
    address.complement = normalizeOrNull(response.complemento);
    address.neighborhood = normalizeOrNull(response.bairro);
    address.city = normalizeOrNull(response.municipio);
    address.state = normalizeOrNull(response.uf);
    address.zipCode = digitsOnly(response.cep);
    dto.address = address;

    return dto;
  }

  @Transactional(readOnly = true)
  public AccountingExportFile exportacaoContabil(String fromRaw, String toRaw, String statusRaw, String formatRaw) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate from = parseRequiredDate(fromRaw, "Periodo inicial (from) obrigatorio.");
    LocalDate to = parseRequiredDate(toRaw, "Periodo final (to) obrigatorio.");
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("Periodo invalido: 'to' deve ser maior ou igual a 'from'.");
    }
    if (to.toEpochDay() - from.toEpochDay() > 365) {
      throw new IllegalArgumentException("Periodo maximo para exportacao contabil e de 365 dias.");
    }

    List<NfseFiscalStatus> statuses = parseFiscalStatusList(statusRaw);
    NfseAccountingExportFormat format = parseAccountingExportFormat(formatRaw);
    List<NfseInvoiceEntity> rows = nfseInvoiceRepository.listForAccountingExport(tenantId, from, to, statuses);

    byte[] bytes;
    String fileName;
    String mediaType;
    if (format == NfseAccountingExportFormat.CSV) {
      String csv = buildAccountingCsv(rows);
      bytes = csv.getBytes(StandardCharsets.UTF_8);
      fileName = "nfse-contabil-" + from + "-" + to + ".csv";
      mediaType = "text/csv";
    } else if (format == NfseAccountingExportFormat.XLSX) {
      bytes = buildAccountingXlsx(rows);
      fileName = "nfse-contabil-" + from + "-" + to + ".xlsx";
      mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    } else {
      bytes = buildAccountingXmlZip(rows);
      fileName = "nfse-xmls-" + from + "-" + to + ".zip";
      mediaType = "application/zip";
    }

    LOG.info(
        "nfse_accounting_export_generated correlationId={} tenant_id={} from={} to={} statuses={} format={} rows={} generatedAt={}",
        resolveCorrelationId(),
        tenantId,
        from,
        to,
        statusesAsString(statuses),
        format.name(),
        rows.size(),
        Instant.now().atOffset(ZoneOffset.UTC));
    return new AccountingExportFile(fileName, mediaType, bytes, rows.size(), format.name());
  }

  private NfseConfigEntity resolveRequiredConfigForInvoice(
      UUID tenantId, NfseAmbiente ambiente, String municipioCodigoIbge) {
    NfseConfigEntity config =
        nfseConfigRepository
            .findByTenantAndAmbiente(tenantId, ambiente)
            .orElseThrow(
                () ->
                    new ApiClientErrorException(
                        "Configuracao NFS-e nao encontrada para o ambiente informado.", 404));
    String configuredMunicipio = normalize(config.getMunicipioCodigoIbge());
    String requestedMunicipio = normalizeOrNull(municipioCodigoIbge);
    if (requestedMunicipio != null && !requestedMunicipio.equals(configuredMunicipio)) {
      throw new IllegalArgumentException("NFSE_MUNICIPIO_DIVERGENTE_DA_CONFIG");
    }
    return config;
  }

  private String resolveMunicipioCodigoIbge(NfseConfigEntity config, String requestedMunicipioCodigoIbge) {
    String configuredMunicipio = config != null ? normalize(config.getMunicipioCodigoIbge()) : null;
    if (configuredMunicipio != null && !configuredMunicipio.isBlank()) return configuredMunicipio;
    return normalize(requestedMunicipioCodigoIbge);
  }

  private void validarInvoiceRequest(NfseDtos.Invoice request, String municipioCodigoIbge, String provedor) {
    if (request.customer == null) throw new IllegalArgumentException("Tomador obrigatorio.");
    if (isBlank(request.customer.name)) throw new IllegalArgumentException("Nome do tomador obrigatorio.");
    NfseCustomerType customerType = parseCustomerType(request.customer.type);
    if (customerType == NfseCustomerType.EXTERIOR) {
      if (isBlank(request.customer.countryCode)) {
        throw new IllegalArgumentException("NFSE_CUSTOMER_EXTERIOR_REQUIRES_COUNTRY");
      }
    } else {
      if (isBlank(request.customer.document)) {
        throw new IllegalArgumentException("NFSE_CUSTOMER_DOCUMENT_REQUIRED");
      }
    }
    if (isBlank(municipioCodigoIbge)) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_MUNICIPIO");
    if (isBlank(provedor)) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_PROVEDOR");
    if (isBlank(request.serieRps)) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_SERIE_RPS");
    if (isBlank(request.naturezaOperacao)) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_NATUREZA_OPERACAO");
    if (isBlank(request.itemListaServico)) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_ITEM_LISTA_SERVICO");
    if (request.aliquotaIss == null) throw new IllegalArgumentException("NFSE_CONFIG_MISSING_ALIQUOTA_ISS");
    if (request.items == null || request.items.isEmpty()) throw new IllegalArgumentException("Ao menos um item e obrigatorio.");
    if ("SEFIN_NACIONAL".equalsIgnoreCase(provedor) && isBlank(request.nationalTaxCode)) {
      boolean allItemsMissingNationalTaxCode = request.items.stream().allMatch(item -> isBlank(item.nationalTaxCode));
      if (allItemsMissingNationalTaxCode) {
        throw new IllegalArgumentException("NFSE_PROVIDER_SEFIN_NACIONAL_CTRIBNAC_REQUIRED");
      }
    }
  }

  private void persistirItens(UUID tenantId, UUID invoiceId, List<NfseDtos.Item> items) {
    if (items == null) return;
    for (NfseDtos.Item item : items) {
      NfseInvoiceItemEntity entity = new NfseInvoiceItemEntity();
      entity.setTenantId(tenantId);
      entity.setInvoiceId(invoiceId);
      entity.setLineNumber(item.lineNumber);
      entity.setDescricaoServico(normalize(item.descricaoServico));
      entity.setQuantidade(nvl(item.quantidade));
      entity.setValorUnitario(nvl(item.valorUnitario));
      entity.setValorTotal(nvl(item.valorTotal));
      entity.setItemListaServico(normalize(item.itemListaServico));
      entity.setCodigoTributacaoMunicipio(normalizeOrNull(item.codigoTributacaoMunicipio));
      entity.setNationalTaxCode(normalizeOrNull(item.nationalTaxCode));
      entity.setNbsCode(normalizeOrNull(item.nbsCode));
      entity.setAliquotaIss(nvl(item.aliquotaIss));
      entity.setValorIss(nvl(item.valorIss));
      nfseInvoiceItemRepository.save(entity);
    }
  }

  private NfseDtos.Invoice toInvoiceDtoFromRequest(NfseInvoiceEntity entity, List<NfseDtos.Item> requestItems) {
    NfseDtos.Invoice dto = toInvoiceDtoWithoutItems(entity);
    if (requestItems != null) {
      dto.items = requestItems;
      return dto;
    }
    List<NfseInvoiceItemEntity> rows =
        nfseInvoiceItemRepository.listByTenantAndInvoice(entity.getTenantId(), entity.getId());
    dto.items = rows.stream().map(this::toItemDto).toList();
    return dto;
  }

  private NfseDtos.Invoice toInvoiceDto(NfseInvoiceEntity entity, List<NfseInvoiceItemEntity> itemEntities) {
    NfseDtos.Invoice dto = toInvoiceDtoWithoutItems(entity);
    dto.items = itemEntities.stream().map(this::toItemDto).toList();
    return dto;
  }

  private NfseDtos.Invoice toInvoiceDtoWithoutItems(NfseInvoiceEntity entity) {
    NfseDtos.Invoice dto = new NfseDtos.Invoice();
    dto.id = entity.getId() != null ? entity.getId().toString() : null;
    dto.appointmentId = entity.getAppointmentId() != null ? entity.getAppointmentId().toString() : null;
    dto.ambiente = entity.getAmbiente() != null ? entity.getAmbiente().name() : null;
    dto.municipioCodigoIbge = entity.getMunicipioCodigoIbge();
    dto.provedor = entity.getProvedor();
    dto.fiscalStatus = entity.getFiscalStatus() != null ? entity.getFiscalStatus().name() : null;
    dto.operationalStatus = entity.getOperationalStatus() != null ? entity.getOperationalStatus().name() : null;
    dto.numeroRps = entity.getNumeroRps();
    dto.serieRps = entity.getSerieRps();
    dto.numeroNfse = entity.getNumeroNfse();
    dto.codigoVerificacao = entity.getCodigoVerificacao();
    dto.chaveAcessoNfse = entity.getChaveAcessoNfse();
    dto.protocolo = entity.getProtocolo();
    dto.dataCompetencia = entity.getDataCompetencia() != null ? entity.getDataCompetencia().toString() : null;
    dto.dataEmissao = entity.getDataEmissao() != null ? entity.getDataEmissao().toString() : null;
    dto.naturezaOperacao = entity.getNaturezaOperacao();
    dto.itemListaServico = entity.getItemListaServico();
    dto.codigoTributacaoMunicipio = entity.getCodigoTributacaoMunicipio();
    dto.nationalTaxCode = entity.getNationalTaxCode();
    dto.nbsCode = entity.getNbsCode();
    dto.localPrestacaoCodigoIbge = entity.getLocalPrestacaoCodigoIbge();
    dto.valorServicos = entity.getValorServicos();
    dto.valorDeducoes = entity.getValorDeducoes();
    dto.valorIss = entity.getValorIss();
    dto.aliquotaIss = entity.getAliquotaIss();
    dto.issRetido = entity.isIssRetido();
    dto.notes = entity.getNotes();
    dto.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    dto.updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null;

    NfseDtos.Customer customer = new NfseDtos.Customer();
    customer.type = entity.getCustomerType() != null ? entity.getCustomerType().name() : null;
    customer.document = entity.getCustomerDocument();
    customer.countryCode = entity.getCustomerCountryCode();
    customer.documentType = entity.getCustomerDocumentType();
    customer.name = entity.getCustomerName();
    customer.email = entity.getCustomerEmail();
    customer.phone = entity.getCustomerPhone();
    dto.customer = customer;

    return dto;
  }

  private NfseDtos.Item toItemDto(NfseInvoiceItemEntity item) {
    NfseDtos.Item dto = new NfseDtos.Item();
    dto.lineNumber = item.getLineNumber();
    dto.descricaoServico = item.getDescricaoServico();
    dto.quantidade = item.getQuantidade();
    dto.valorUnitario = item.getValorUnitario();
    dto.valorTotal = item.getValorTotal();
    dto.itemListaServico = item.getItemListaServico();
    dto.codigoTributacaoMunicipio = item.getCodigoTributacaoMunicipio();
    dto.nationalTaxCode = item.getNationalTaxCode();
    dto.nbsCode = item.getNbsCode();
    dto.aliquotaIss = item.getAliquotaIss();
    dto.valorIss = item.getValorIss();
    return dto;
  }

  private void registrarEvento(
      UUID tenantId,
      UUID invoiceId,
      String eventType,
      String eventStatus,
      String providerCode,
      String providerMessage,
      String payloadSource) {
    NfseInvoiceEventEntity event = new NfseInvoiceEventEntity();
    event.setTenantId(tenantId);
    event.setInvoiceId(invoiceId);
    event.setEventType(eventType);
    event.setEventStatus(eventStatus);
    event.setProviderCode(providerCode);
    event.setProviderMessage(providerMessage);
    event.setPayloadHash(payloadSource == null ? null : sha256(payloadSource));
    event.setRequestedBy(authenticatedUser.idOuNulo());
    nfseInvoiceEventRepository.save(event);
  }

  private void transicionarFiscalStatus(
      NfseInvoiceEntity entity, NfseFiscalStatus target, UUID tenantId, UUID invoiceId, String eventType) {
    NfseFiscalStatus current = entity.getFiscalStatus();
    if (current == target) return;
    if (!nfseFiscalStateMachine.canReach(current, target)) {
      throw new NfseStateTransitionException("Transicao fiscal NFS-e invalida: " + current + " -> " + target);
    }
    entity.setFiscalStatus(target);
    registrarEvento(tenantId, invoiceId, eventType, target.name(), null, null, null);
    LOG.info(
        "nfse_status_transition correlationId={} tenant_id={} invoice_id={} ambiente={} municipio={} fiscal_from={} fiscal_to={}",
        resolveCorrelationId(),
        tenantId,
        invoiceId,
        entity.getAmbiente(),
        entity.getMunicipioCodigoIbge(),
        current,
        target);
  }

  private NfseAmbiente parseAmbiente(String value) {
    String raw = isBlank(value) ? NfseAmbiente.HOMOLOGACAO.name() : value;
    try {
      return NfseAmbiente.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Ambiente NFS-e invalido.");
    }
  }

  private NfseCustomerType parseCustomerType(String value) {
    if (isBlank(value)) throw new IllegalArgumentException("Tipo do tomador obrigatorio.");
    try {
      return NfseCustomerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Tipo do tomador invalido.");
    }
  }

  private NfseFiscalStatus parseFiscalStatus(String value) {
    if (isBlank(value)) throw new IllegalArgumentException("Status fiscal invalido.");
    try {
      return NfseFiscalStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Status fiscal invalido.");
    }
  }

  private NfseAccountingExportFormat parseAccountingExportFormat(String raw) {
    if (isBlank(raw)) return NfseAccountingExportFormat.CSV;
    try {
      return NfseAccountingExportFormat.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Formato de exportacao invalido. Use CSV, XLSX ou ZIP_XML.");
    }
  }

  private List<NfseFiscalStatus> parseFiscalStatusList(String value) {
    if (isBlank(value)) return List.of(NfseFiscalStatus.AUTHORIZED, NfseFiscalStatus.CANCELLED);
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(v -> !v.isBlank())
        .map(this::parseFiscalStatus)
        .distinct()
        .toList();
  }

  private LocalDate parseRequiredDate(String value, String errorMessage) {
    if (isBlank(value)) throw new IllegalArgumentException(errorMessage);
    try {
      return LocalDate.parse(value.trim());
    } catch (Exception ex) {
      throw new IllegalArgumentException("Data invalida. Use formato YYYY-MM-DD.");
    }
  }

  private UUID parseUuid(String raw) {
    try {
      return UUID.fromString(raw);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Identificador NFS-e invalido.");
    }
  }

  private UUID parseUuidOrNull(String raw) {
    if (isBlank(raw)) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private LocalDate parseDateOrToday(String value) {
    if (isBlank(value)) return LocalDate.now();
    try {
      return LocalDate.parse(value);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Data de competencia invalida.");
    }
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String normalize(String value) {
    if (isBlank(value)) throw new IllegalArgumentException("Campo obrigatorio ausente.");
    return value.trim();
  }

  private String normalizeOrNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String digitsOnly(String value) {
    if (value == null || value.isBlank()) return "";
    return value.replaceAll("\\D", "");
  }

  private boolean isPendingAuthorizationResult(NfseProviderAdapter.AuthorizationResult result) {
    if (result == null) return false;
    String status = result.providerStatusCode();
    if ("102".equals(status) || "103".equals(status)) return true;
    boolean hasNumeroNfse = result.numeroNfse() != null && !result.numeroNfse().isBlank();
    boolean hasProtocolo = result.protocolo() != null && !result.protocolo().isBlank();
    return !hasNumeroNfse && hasProtocolo;
  }

  private byte[] asEncryptedBytes(String xml) {
    String encrypted = encryptionService.encrypt(xml);
    return encrypted == null ? null : encrypted.getBytes(StandardCharsets.UTF_8);
  }

  /** Le {@code X-Request-Id} do request atual, mesmo mecanismo de {@link ContextoTenant}. */
  private String resolveCorrelationId() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) return null;
    HttpServletRequest request = attributes.getRequest();
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) requestId = request.getHeader("x-request-id");
    return requestId;
  }

  private void registrarMetricasAutorizacao(
      boolean success, NfseInvoiceEntity entity, Instant startedAt, String providerStatusCode) {
    if (meterRegistry == null) return;
    String municipio = entity != null && entity.getMunicipioCodigoIbge() != null ? entity.getMunicipioCodigoIbge() : "UNKNOWN";
    String provedor = entity != null && entity.getProvedor() != null ? entity.getProvedor() : "UNKNOWN";
    String ambiente = entity != null && entity.getAmbiente() != null ? entity.getAmbiente().name() : "UNKNOWN";
    String statusCode = providerStatusCode == null ? "NA" : providerStatusCode;
    meterRegistry
        .counter(
            "nfse.authorization.total",
            "success", String.valueOf(success),
            "municipio", municipio,
            "provedor", provedor,
            "ambiente", ambiente,
            "status_code", statusCode)
        .increment();
    if (!success) {
      meterRegistry
          .counter("nfse.rejection.total", "municipio", municipio, "provedor", provedor, "ambiente", ambiente)
          .increment();
      if ("408".equals(statusCode)
          || "429".equals(statusCode)
          || "502".equals(statusCode)
          || "503".equals(statusCode)
          || "504".equals(statusCode)) {
        meterRegistry
            .counter("nfse.timeout.total", "municipio", municipio, "provedor", provedor, "ambiente", ambiente)
            .increment();
      }
    }
    Timer.builder("nfse.authorization.latency")
        .tag("municipio", municipio)
        .tag("provedor", provedor)
        .tag("ambiente", ambiente)
        .tag("success", String.valueOf(success))
        .register(meterRegistry)
        .record(Duration.between(startedAt, Instant.now()));
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception ex) {
      return null;
    }
  }

  private String statusesAsString(List<NfseFiscalStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) return "ALL";
    StringJoiner joiner = new StringJoiner(",");
    for (NfseFiscalStatus status : statuses) {
      joiner.add(status.name());
    }
    return joiner.toString();
  }

  private String buildAccountingCsv(List<NfseInvoiceEntity> rows) {
    StringBuilder csv = new StringBuilder();
    csv.append(
        "invoice_id,tenant_id,ambiente,provedor,municipio_codigo_ibge,data_competencia,data_emissao,fiscal_status,numero_rps,serie_rps,numero_nfse,protocolo,codigo_verificacao,customer_type,customer_document,customer_name,valor_servicos,valor_deducoes,valor_iss,aliquota_iss,iss_retido,item_lista_servico,codigo_tributacao_municipio\n");
    for (NfseInvoiceEntity row : rows) {
      csv.append(csvCell(row.getId()))
          .append(',').append(csvCell(row.getTenantId()))
          .append(',').append(csvCell(row.getAmbiente() != null ? row.getAmbiente().name() : null))
          .append(',').append(csvCell(row.getProvedor()))
          .append(',').append(csvCell(row.getMunicipioCodigoIbge()))
          .append(',').append(csvCell(row.getDataCompetencia()))
          .append(',').append(csvCell(row.getDataEmissao()))
          .append(',').append(csvCell(row.getFiscalStatus() != null ? row.getFiscalStatus().name() : null))
          .append(',').append(csvCell(row.getNumeroRps()))
          .append(',').append(csvCell(row.getSerieRps()))
          .append(',').append(csvCell(row.getNumeroNfse()))
          .append(',').append(csvCell(row.getProtocolo()))
          .append(',').append(csvCell(row.getCodigoVerificacao()))
          .append(',').append(csvCell(row.getCustomerType() != null ? row.getCustomerType().name() : null))
          .append(',').append(csvCell(row.getCustomerDocument()))
          .append(',').append(csvCell(row.getCustomerName()))
          .append(',').append(csvCell(row.getValorServicos()))
          .append(',').append(csvCell(row.getValorDeducoes()))
          .append(',').append(csvCell(row.getValorIss()))
          .append(',').append(csvCell(row.getAliquotaIss()))
          .append(',').append(csvCell(row.isIssRetido()))
          .append(',').append(csvCell(row.getItemListaServico()))
          .append(',').append(csvCell(row.getCodigoTributacaoMunicipio()))
          .append('\n');
    }
    return csv.toString();
  }

  private byte[] buildAccountingXlsx(List<NfseInvoiceEntity> rows) {
    try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      XSSFSheet sheet = workbook.createSheet("NFS-e contabil");
      String[] headers =
          new String[] {
            "invoice_id", "tenant_id", "ambiente", "provedor", "municipio_codigo_ibge", "data_competencia",
            "data_emissao", "fiscal_status", "numero_rps", "serie_rps", "numero_nfse", "protocolo",
            "codigo_verificacao", "customer_type", "customer_document", "customer_name", "valor_servicos",
            "valor_deducoes", "valor_iss", "aliquota_iss", "iss_retido", "item_lista_servico",
            "codigo_tributacao_municipio"
          };
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
      }

      int rowIndex = 1;
      for (NfseInvoiceEntity row : rows) {
        Row dataRow = sheet.createRow(rowIndex++);
        int col = 0;
        dataRow.createCell(col++).setCellValue(stringValue(row.getId()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getTenantId()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getAmbiente() != null ? row.getAmbiente().name() : null));
        dataRow.createCell(col++).setCellValue(stringValue(row.getProvedor()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getMunicipioCodigoIbge()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getDataCompetencia()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getDataEmissao()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getFiscalStatus() != null ? row.getFiscalStatus().name() : null));
        dataRow.createCell(col++).setCellValue(stringValue(row.getNumeroRps()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getSerieRps()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getNumeroNfse()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getProtocolo()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getCodigoVerificacao()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getCustomerType() != null ? row.getCustomerType().name() : null));
        dataRow.createCell(col++).setCellValue(stringValue(row.getCustomerDocument()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getCustomerName()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getValorServicos()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getValorDeducoes()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getValorIss()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getAliquotaIss()));
        dataRow.createCell(col++).setCellValue(stringValue(row.isIssRetido()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getItemListaServico()));
        dataRow.createCell(col++).setCellValue(stringValue(row.getCodigoTributacaoMunicipio()));
      }

      for (int i = 0; i < headers.length; i++) {
        sheet.setColumnWidth(i, Math.max(headers[i].length() * 400 + 2000, 4000));
      }
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Falha ao gerar exportacao XLSX de NFS-e.", ex);
    }
  }

  private byte[] buildAccountingXmlZip(List<NfseInvoiceEntity> rows) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
      String manifest = buildAccountingCsv(rows);
      zip.putNextEntry(new ZipEntry("manifest.csv"));
      zip.write(manifest.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();

      for (NfseInvoiceEntity row : rows) {
        String invoiceKey = row.getId() != null ? row.getId().toString() : UUID.randomUUID().toString();
        String envio = decryptStoredXml(row.getXmlEnvioEnc());
        String retorno = decryptStoredXml(row.getXmlRetornoEnc());
        if (!isBlank(envio)) {
          zip.putNextEntry(new ZipEntry("xml/" + invoiceKey + "/envio.xml"));
          zip.write(envio.getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
        if (!isBlank(retorno)) {
          zip.putNextEntry(new ZipEntry("xml/" + invoiceKey + "/retorno.xml"));
          zip.write(retorno.getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
      zip.finish();
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Falha ao gerar pacote ZIP de XMLs NFS-e.", ex);
    }
  }

  private String decryptStoredXml(byte[] encryptedPayload) {
    if (encryptedPayload == null || encryptedPayload.length == 0) return null;
    String encrypted = new String(encryptedPayload, StandardCharsets.UTF_8);
    if (encrypted.isBlank()) return null;
    try {
      return encryptionService.decrypt(encrypted);
    } catch (RuntimeException ex) {
      LOG.warn("nfse_xml_decrypt_failed payload_size={}", encryptedPayload.length);
      return null;
    }
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String csvCell(Object value) {
    if (value == null) return "\"\"";
    String raw = String.valueOf(value);
    String escaped = raw.replace("\"", "\"\"");
    return "\"" + escaped + "\"";
  }

  public record AccountingExportFile(String fileName, String mediaType, byte[] content, int rowsCount, String format) {}
}
