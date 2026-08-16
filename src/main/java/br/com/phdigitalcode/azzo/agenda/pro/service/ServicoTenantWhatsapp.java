package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantWhatsAppDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupGateway;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;

/** Espelha {@code modules/tenant/application/ServicoTenantWhatsapp.java}. */
@Service
public class ServicoTenantWhatsapp {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoTenantWhatsapp.class);

  private static final String ONBOARDING_NOT_STARTED = "NOT_STARTED";
  private static final String ONBOARDING_PENDING_EXCHANGE = "PENDING_EXCHANGE";
  private static final String ONBOARDING_CONNECTED = "CONNECTED";
  private static final String ONBOARDING_FAILED = "FAILED";
  private static final String TOKEN_SOURCE_MANUAL = "MANUAL";
  private static final String TOKEN_SOURCE_EMBEDDED = "EMBEDDED_CODE_EXCHANGE";

  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final EncryptionService encryptionService;
  private final WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private final WhatsAppClient whatsAppClient;
  private final MetaEmbeddedSignupGateway metaEmbeddedSignupClient;
  private final WhatsAppMessageLogRepository messageLogRepository;
  private final boolean embeddedSignupEnabled;

  public ServicoTenantWhatsapp(
      ContextoTenant contextoTenant,
      AuditService auditService,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      EncryptionService encryptionService,
      WebhookVerifyTokenHashService webhookVerifyTokenHashService,
      WhatsAppClient whatsAppClient,
      MetaEmbeddedSignupGateway metaEmbeddedSignupClient,
      WhatsAppMessageLogRepository messageLogRepository,
      @Value("${app.whatsapp.embedded-signup.enabled:false}") boolean embeddedSignupEnabled) {
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.encryptionService = encryptionService;
    this.webhookVerifyTokenHashService = webhookVerifyTokenHashService;
    this.whatsAppClient = whatsAppClient;
    this.metaEmbeddedSignupClient = metaEmbeddedSignupClient;
    this.messageLogRepository = messageLogRepository;
    this.embeddedSignupEnabled = embeddedSignupEnabled;
  }

  @Transactional
  public TenantWhatsAppDtos.ConfigResponse atualizar(TenantWhatsAppDtos.UpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    String accessToken = trimToNull(request.accessToken);
    String candidateAccessToken = accessToken != null ? accessToken : decryptAccessToken(config);
    String candidatePhoneNumberId = trimToNull(request.phoneNumberId);
    WhatsAppClient.PhoneNumberDetails phoneDetails = validatePhoneNumberConfiguration(candidateAccessToken, candidatePhoneNumberId);

    if (accessToken != null) {
      config.setWhatsappAccessTokenEnc(encryptionService.encrypt(accessToken));
      config.setWhatsappTokenSource(TOKEN_SOURCE_MANUAL);
    } else if (!hasAccessTokenConfigured(config)) {
      throw new IllegalArgumentException("Token do WhatsApp e obrigatorio na primeira configuracao");
    }
    config.setWhatsappPhoneNumberId(candidatePhoneNumberId);
    config.setWhatsappBusinessAccountId(trimToNull(request.businessAccountId));
    config.setMetaBusinessId(trimToNull(request.businessId));
    config.setDisplayPhoneNumber(firstNonBlank(
        trimToNull(request.displayPhoneNumber),
        phoneDetails != null ? phoneDetails.displayPhoneNumber : null,
        config.getDisplayPhoneNumber()));
    String webhookVerifyToken = trimToNull(request.webhookVerifyToken);
    if (webhookVerifyToken != null) {
      assignWebhookVerifyToken(config, webhookVerifyToken);
    } else {
      ensureWebhookVerifyToken(config);
    }
    config.setWhatsappEnabled(request.whatsappEnabled);
    if (config.getWhatsappPhoneNumberId() == null || config.getWhatsappPhoneNumberId().isBlank()) {
      config.setWhatsappOnboardingStatus(ONBOARDING_NOT_STARTED);
    } else if (hasAccessTokenConfigured(config)) {
      config.setWhatsappOnboardingStatus(ONBOARDING_CONNECTED);
    }
    if (request.usageProfile != null && isValidUsageProfile(request.usageProfile)) {
      config.setWhatsappUsageProfile(request.usageProfile);
      applyProfileDefaults(config, request.usageProfile);
    }
    if (request.canSchedule != null) config.setCanSchedule(request.canSchedule);
    if (request.canCancel != null) config.setCanCancel(request.canCancel);
    if (request.canReschedule != null) config.setCanReschedule(request.canReschedule);
    if (request.confirmationMessageTemplate != null) config.setConfirmationMessageTemplate(trimToNull(request.confirmationMessageTemplate));
    if (request.cancellationMessageTemplate != null) config.setCancellationMessageTemplate(trimToNull(request.cancellationMessageTemplate));
    if (request.reminderMessageTemplate != null) config.setReminderMessageTemplate(trimToNull(request.reminderMessageTemplate));
    tenantWhatsAppConfigRepository.save(config);
    TenantWhatsAppDtos.ConfigResponse result = toConfigResponse(config);
    registrarAuditoria(tenantId, "WHATSAPP_CONFIG_UPDATE", tenantId.toString(), null, result, true);
    return result;
  }

  @Transactional
  public TenantWhatsAppDtos.ConfigResponse atualizarPreferencias(TenantWhatsAppDtos.SettingsPatchRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    if (request.whatsappEnabled != null) config.setWhatsappEnabled(request.whatsappEnabled);
    if (request.usageProfile != null && isValidUsageProfile(request.usageProfile)) {
      config.setWhatsappUsageProfile(request.usageProfile);
      applyProfileDefaults(config, request.usageProfile);
    }
    if (request.canSchedule != null) config.setCanSchedule(request.canSchedule);
    if (request.canCancel != null) config.setCanCancel(request.canCancel);
    if (request.canReschedule != null) config.setCanReschedule(request.canReschedule);
    tenantWhatsAppConfigRepository.save(config);
    TenantWhatsAppDtos.ConfigResponse result = toConfigResponse(config);
    registrarAuditoria(tenantId, "WHATSAPP_SETTINGS_PATCH", tenantId.toString(), null, result, true);
    return result;
  }

  @Transactional
  public TenantWhatsAppDtos.TestResponse testarConexao() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    TenantWhatsAppDtos.TestResponse response = new TenantWhatsAppDtos.TestResponse();
    try {
      boolean ok = whatsAppClient.testConnection(config);
      config.setWhatsappEnabled(ok);
      tenantWhatsAppConfigRepository.save(config);

      response.success = ok;
      response.whatsappEnabled = config.isWhatsappEnabled();
      response.message = "Conexao validada com sucesso.";
      registrarAuditoria(tenantId, "WHATSAPP_CONNECTION_TEST", tenantId.toString(), null, java.util.Map.of("success", ok), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.whatsappEnabled = Boolean.TRUE.equals(config.isWhatsappEnabled());
      response.message = mapTestConnectionError(ex);
      registrarAuditoria(tenantId, "WHATSAPP_CONNECTION_TEST", tenantId.toString(), null, java.util.Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  public TenantWhatsAppDtos.ValidateResponse validarConfiguracao(TenantWhatsAppDtos.ValidateRequest request) {
    WhatsAppClient.PhoneNumberDetails details =
        whatsAppClient.fetchPhoneNumberDetails(trimToNull(request.accessToken), trimToNull(request.phoneNumberId));
    TenantWhatsAppDtos.ValidateResponse response = new TenantWhatsAppDtos.ValidateResponse();
    response.success = true;
    response.message = "Conexao com a Meta validada com sucesso.";
    response.phoneNumberId = details.id;
    response.displayPhoneNumber = details.displayPhoneNumber;
    response.verifiedName = details.verifiedName;
    return response;
  }

  @Transactional
  public TenantWhatsAppDtos.TestMessageResponse enviarMensagemTeste(TenantWhatsAppDtos.TestMessageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    TenantWhatsAppDtos.TestMessageResponse response = new TenantWhatsAppDtos.TestMessageResponse();
    try {
      String providerMessageId = whatsAppClient.sendMessage(
          config,
          trimToNull(request.destinationPhone),
          firstNonBlank(trimToNull(request.message), "Mensagem de teste do AZZO Agenda Pro."));
      response.success = true;
      response.providerMessageId = providerMessageId;
      response.message = "Mensagem de teste enviada com sucesso.";
      registrarAuditoria(tenantId, "WHATSAPP_TEST_MESSAGE", tenantId.toString(), null, java.util.Map.of("success", true, "destination", trimToNull(request.destinationPhone) != null ? trimToNull(request.destinationPhone) : ""), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.message = mapTestConnectionError(ex);
      registrarAuditoria(tenantId, "WHATSAPP_TEST_MESSAGE", tenantId.toString(), null, java.util.Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  private String mapTestConnectionError(Exception error) {
    String message = error == null || error.getMessage() == null ? "" : error.getMessage().toLowerCase();
    if (message.contains("token do whatsapp nao configurado")) {
      return "Token de acesso do WhatsApp nao configurado. Salve a configuracao antes de testar.";
    }
    if (message.contains("phonenumberid do whatsapp nao configurado")) {
      return "Phone Number ID do WhatsApp nao configurado. Revise os dados e tente novamente.";
    }
    if (message.contains("invalid oauth access token")
        || message.contains("token nao autorizado")
        || message.contains("token n")
        || message.contains("not authorized")) {
      return "Falha ao validar a conexao com o WhatsApp. Revise o token de acesso informado e tente novamente.";
    }
    return "Falha ao validar a conexao com o WhatsApp. Revise as credenciais configuradas e tente novamente.";
  }

  @Transactional
  public TenantWhatsAppDtos.ConfigResponse obterConfiguracaoAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return toConfigResponse(config);
  }

  @Transactional
  public TenantWhatsAppDtos.EmbeddedSignupStatusResponse concluirEmbeddedSignup(
      TenantWhatsAppDtos.EmbeddedSignupCompleteRequest request) {
    if (!embeddedSignupEnabled) {
      TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = obterStatusEmbeddedSignup();
      response.connected = false;
      response.lastError = "Embedded Signup desabilitado neste ambiente.";
      return response;
    }
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);

    String code = trimToNull(request.code);
    String phoneNumberId = request.setupInfo != null ? trimToNull(request.setupInfo.phoneNumberId) : null;
    String businessAccountId = request.setupInfo != null ? trimToNull(request.setupInfo.wabaId) : null;
    String businessId = request.setupInfo != null ? trimToNull(request.setupInfo.businessId) : null;

    config.setWhatsappOnboardingStatus(ONBOARDING_PENDING_EXCHANGE);
    config.setEmbeddedSignupLastError(null);

    try {
      String accessToken = metaEmbeddedSignupClient.exchangeCodeForAccessToken(code);
      MetaEmbeddedSignupClient.PhoneNumberDetails phoneDetails =
          metaEmbeddedSignupClient.fetchPhoneNumberDetails(accessToken, phoneNumberId);

      config.setWhatsappAccessTokenEnc(encryptionService.encrypt(accessToken));
      config.setWhatsappPhoneNumberId(phoneDetails.id != null ? phoneDetails.id : phoneNumberId);
      config.setWhatsappBusinessAccountId(businessAccountId);
      config.setMetaBusinessId(businessId);
      config.setDisplayPhoneNumber(
          firstNonBlank(phoneDetails.displayPhoneNumber, request.setupInfo.phoneNumber, config.getDisplayPhoneNumber()));
      config.setWhatsappTokenSource(TOKEN_SOURCE_EMBEDDED);
      config.setWhatsappOnboardingStatus(ONBOARDING_CONNECTED);
      config.setEmbeddedSignupCompletedAt(Instant.now());
      config.setEmbeddedSignupLastError(null);
      config.setWhatsappEnabled(true);
      ensureWebhookVerifyToken(config);

      whatsAppClient.testConnection(config);
      tenantWhatsAppConfigRepository.save(config);
      TenantWhatsAppDtos.EmbeddedSignupStatusResponse signupResult = toEmbeddedSignupStatus(config);
      registrarAuditoria(tenantId, "WHATSAPP_EMBEDDED_SIGNUP_COMPLETE", tenantId.toString(), null, java.util.Map.of("status", ONBOARDING_CONNECTED), true);
      return signupResult;
    } catch (RuntimeException e) {
      config.setWhatsappEnabled(false);
      config.setWhatsappOnboardingStatus(ONBOARDING_FAILED);
      config.setEmbeddedSignupLastError(sanitizeEmbeddedError(e.getMessage()));
      tenantWhatsAppConfigRepository.save(config);
      registrarAuditoria(tenantId, "WHATSAPP_EMBEDDED_SIGNUP_COMPLETE", tenantId.toString(), null, java.util.Map.of("status", ONBOARDING_FAILED), false);
      return toEmbeddedSignupStatus(config);
    }
  }

  @Transactional
  public TenantWhatsAppDtos.EmbeddedSignupStatusResponse obterStatusEmbeddedSignup() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return toEmbeddedSignupStatus(config);
  }

  public TenantWhatsAppDtos.MessageLogResponse listarMensagens(int limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
    List<WhatsAppMessageLogEntity> fetched =
        messageLogRepository.findByTenantIdOrderBySentAtDesc(tenantId, PageRequest.of(0, normalizedLimit + 1));
    boolean hasMore = fetched.size() > normalizedLimit;
    List<WhatsAppMessageLogEntity> page = fetched.stream().limit(normalizedLimit).toList();
    TenantWhatsAppDtos.MessageLogResponse response = new TenantWhatsAppDtos.MessageLogResponse();
    response.items = page.stream().map(e -> {
      TenantWhatsAppDtos.MessageLogItem item = new TenantWhatsAppDtos.MessageLogItem();
      item.id = e.getId() != null ? e.getId().toString() : null;
      item.eventType = e.getEventType();
      item.destinationPhone = e.getDestinationPhone();
      item.messageText = e.getMessageText();
      item.providerMessageId = e.getProviderMessageId();
      item.status = e.getStatus();
      item.errorMessage = e.getErrorMessage();
      item.sentAt = e.getSentAt() != null ? e.getSentAt().toString() : null;
      item.appointmentId = e.getAppointmentId() != null ? e.getAppointmentId().toString() : null;
      return item;
    }).toList();
    response.hasMore = hasMore;
    if (hasMore && !page.isEmpty()) {
      WhatsAppMessageLogEntity last = page.get(page.size() - 1);
      response.nextCursorSentAt = last.getSentAt() != null ? last.getSentAt().toString() : null;
    }
    return response;
  }

  private TenantWhatsAppDtos.ConfigResponse toConfigResponse(TenantWhatsAppConfig config) {
    TenantWhatsAppDtos.ConfigResponse response = new TenantWhatsAppDtos.ConfigResponse();
    response.phoneNumberId = config.getWhatsappPhoneNumberId();
    response.businessAccountId = config.getWhatsappBusinessAccountId();
    response.businessId = config.getMetaBusinessId();
    response.displayPhoneNumber = config.getDisplayPhoneNumber();
    response.webhookVerifyToken = decryptWebhookVerifyToken(config);
    response.accessTokenConfigured = hasAccessTokenConfigured(config);
    response.webhookVerifyTokenConfigured =
        config.getWhatsappWebhookVerifyTokenEnc() != null && !config.getWhatsappWebhookVerifyTokenEnc().isBlank();
    response.whatsappEnabled = config.isWhatsappEnabled();
    response.onboardingStatus = defaultOnboardingStatus(config.getWhatsappOnboardingStatus());
    response.tokenSource = defaultTokenSource(config.getWhatsappTokenSource());
    response.embeddedSignupEnabled = embeddedSignupEnabled;
    response.usageProfile = config.getWhatsappUsageProfile() != null ? config.getWhatsappUsageProfile() : "COMPLETE";
    response.canSchedule = config.isCanSchedule();
    response.canCancel = config.isCanCancel();
    response.canReschedule = config.isCanReschedule();
    response.confirmationMessageTemplate = config.getConfirmationMessageTemplate();
    response.cancellationMessageTemplate = config.getCancellationMessageTemplate();
    response.reminderMessageTemplate = config.getReminderMessageTemplate();
    return response;
  }

  private TenantWhatsAppDtos.EmbeddedSignupStatusResponse toEmbeddedSignupStatus(TenantWhatsAppConfig config) {
    TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = new TenantWhatsAppDtos.EmbeddedSignupStatusResponse();
    response.connected = ONBOARDING_CONNECTED.equals(defaultOnboardingStatus(config.getWhatsappOnboardingStatus()));
    response.whatsappEnabled = config.isWhatsappEnabled();
    response.accessTokenConfigured = hasAccessTokenConfigured(config);
    response.webhookVerifyTokenConfigured =
        config.getWhatsappWebhookVerifyTokenEnc() != null && !config.getWhatsappWebhookVerifyTokenEnc().isBlank();
    response.webhookVerifyToken = decryptWebhookVerifyToken(config);
    response.onboardingStatus = defaultOnboardingStatus(config.getWhatsappOnboardingStatus());
    response.tokenSource = defaultTokenSource(config.getWhatsappTokenSource());
    response.phoneNumberId = config.getWhatsappPhoneNumberId();
    response.businessAccountId = config.getWhatsappBusinessAccountId();
    response.businessId = config.getMetaBusinessId();
    response.displayPhoneNumber = config.getDisplayPhoneNumber();
    response.lastError = config.getEmbeddedSignupLastError();
    response.embeddedSignupEnabled = embeddedSignupEnabled;
    return response;
  }

  private boolean hasAccessTokenConfigured(TenantWhatsAppConfig config) {
    return config != null
        && config.getWhatsappAccessTokenEnc() != null
        && !config.getWhatsappAccessTokenEnc().isBlank();
  }

  private void registrarAuditoria(UUID tenantId, String action, String entityId, Object before, Object after, boolean success) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.WHATSAPP;
      command.action = action;
      command.entityType = "WHATSAPP_CONFIG";
      command.entityId = entityId;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      if (success) {
        auditService.recordSuccess(command);
      } else {
        auditService.recordError(command);
      }
    } catch (Exception e) {
      LOG.warn("Falha ao registrar auditoria whatsapp action={}", action, e);
    }
  }

  private boolean isValidUsageProfile(String profile) {
    return "REACTIVE_ONLY".equals(profile) || "NOTIFICATIONS".equals(profile) || "COMPLETE".equals(profile);
  }

  private void applyProfileDefaults(TenantWhatsAppConfig config, String profile) {
    switch (profile) {
      case "REACTIVE_ONLY" -> {
        config.setCanSchedule(false);
        config.setCanCancel(false);
        config.setCanReschedule(false);
      }
      case "NOTIFICATIONS" -> {
        config.setCanSchedule(true);
        config.setCanCancel(true);
        config.setCanReschedule(true);
      }
      case "COMPLETE" -> {
        config.setCanSchedule(true);
        config.setCanCancel(true);
        config.setCanReschedule(true);
      }
    }
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String decryptAccessToken(TenantWhatsAppConfig config) {
    if (!hasAccessTokenConfigured(config)) return null;
    return trimToNull(encryptionService.decrypt(config.getWhatsappAccessTokenEnc()));
  }

  private WhatsAppClient.PhoneNumberDetails validatePhoneNumberConfiguration(String accessToken, String phoneNumberId) {
    if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
      return null;
    }
    return whatsAppClient.fetchPhoneNumberDetails(accessToken, phoneNumberId);
  }

  private void ensureWebhookVerifyToken(TenantWhatsAppConfig config) {
    if (config == null) return;
    if (config.getWhatsappWebhookVerifyTokenEnc() != null && !config.getWhatsappWebhookVerifyTokenEnc().isBlank()) {
      return;
    }
    assignWebhookVerifyToken(config, generateWebhookVerifyToken());
  }

  private void assignWebhookVerifyToken(TenantWhatsAppConfig config, String rawToken) {
    String normalized = trimToNull(rawToken);
    config.setWhatsappWebhookVerifyTokenEnc(encryptionService.encrypt(normalized));
    config.setWhatsappWebhookVerifyTokenHash(webhookVerifyTokenHashService.hash(normalized));
  }

  private String decryptWebhookVerifyToken(TenantWhatsAppConfig config) {
    if (config == null
        || config.getWhatsappWebhookVerifyTokenEnc() == null
        || config.getWhatsappWebhookVerifyTokenEnc().isBlank()) {
      return null;
    }
    return trimToNull(encryptionService.decrypt(config.getWhatsappWebhookVerifyTokenEnc()));
  }

  private String generateWebhookVerifyToken() {
    return "wa_verify_" + UUID.randomUUID().toString().replace("-", "");
  }

  private String defaultOnboardingStatus(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? ONBOARDING_NOT_STARTED : normalized;
  }

  private String defaultTokenSource(String value) {
    String normalized = trimToNull(value);
    return normalized == null ? TOKEN_SOURCE_MANUAL : normalized;
  }

  private String sanitizeEmbeddedError(String message) {
    if (message == null || message.isBlank()) return "Falha ao concluir Embedded Signup.";
    String sanitized = message.replaceAll("[\\r\\n]+", " ").trim();
    return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) return normalized;
    }
    return null;
  }
}
