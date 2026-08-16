package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantTelegramDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TelegramBotClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;

/** Espelha {@code modules/tenant/application/ServicoTenantTelegram.java}. */
@Service
public class ServicoTenantTelegram {

  private static final SecureRandom RANDOM = new SecureRandom();

  /** chat_id do Bot API: id numerico (negativo para grupos/canais) ou @usuario. Telefone nao e aceito. */
  private static final Pattern CHAT_ID_PATTERN = Pattern.compile("^(-?\\d{1,20}|@[A-Za-z0-9_]{4,32})$");

  private static final String INVALID_CHAT_ID_MESSAGE =
      "Destino invalido. O Telegram nao envia por telefone: informe o chat ID numerico "
          + "(ex.: 123456789) ou @usuario. Peca a pessoa iniciar uma conversa com o bot e "
          + "consulte o chat ID com o @userinfobot.";

  private final ContextoTenant contextoTenant;
  private final AuditService auditService;
  private final TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private final EncryptionService encryptionService;
  private final WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private final TelegramBotClient telegramBotClient;
  private final String publicBaseUrl;

  public ServicoTenantTelegram(
      ContextoTenant contextoTenant,
      AuditService auditService,
      TenantTelegramConfigRepository tenantTelegramConfigRepository,
      EncryptionService encryptionService,
      WebhookVerifyTokenHashService webhookVerifyTokenHashService,
      TelegramBotClient telegramBotClient,
      @Value("${app.public-base-url:https://localhost:8443}") String publicBaseUrl) {
    this.contextoTenant = contextoTenant;
    this.auditService = auditService;
    this.tenantTelegramConfigRepository = tenantTelegramConfigRepository;
    this.encryptionService = encryptionService;
    this.webhookVerifyTokenHashService = webhookVerifyTokenHashService;
    this.telegramBotClient = telegramBotClient;
    this.publicBaseUrl = publicBaseUrl;
  }

  @Transactional
  public TenantTelegramDtos.ConfigResponse atualizar(TenantTelegramDtos.UpdateRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantTelegramConfig config = tenantTelegramConfigRepository.findByTenantIdOrCreate(tenantId);

    String botToken = trimToNull(request.botToken);
    if (botToken != null) {
      config.setTelegramBotTokenEnc(encryptionService.encrypt(botToken));
    } else if (!hasBotTokenConfigured(config)) {
      throw new IllegalArgumentException("Token do bot do Telegram e obrigatorio na primeira configuracao.");
    }

    String webhookSecretToken = trimToNull(request.webhookSecretToken);
    if (webhookSecretToken != null) {
      assignWebhookSecret(config, webhookSecretToken);
    } else {
      ensureWebhookSecret(config);
    }

    TelegramBotClient.BotProfile profile = telegramBotClient.getMe(config);
    config.setTelegramBotUsername(firstNonBlank(trimToNull(request.botUsername), profile.username, config.getTelegramBotUsername()));
    config.setTelegramEnabled(request.telegramEnabled);
    sincronizarWebhook(config);
    tenantTelegramConfigRepository.save(config);

    TenantTelegramDtos.ConfigResponse response = toConfigResponse(config);
    registrarAuditoria(tenantId, "TELEGRAM_CONFIG_UPDATE", response, true);
    return response;
  }

  @Transactional
  public TenantTelegramDtos.TestResponse testarConexao() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantTelegramConfig config = tenantTelegramConfigRepository.findByTenantIdOrCreate(tenantId);

    TenantTelegramDtos.TestResponse response = new TenantTelegramDtos.TestResponse();
    try {
      TelegramBotClient.BotProfile profile = telegramBotClient.getMe(config);
      ensureWebhookSecret(config);
      config.setTelegramEnabled(true);
      config.setTelegramBotUsername(firstNonBlank(profile.username, config.getTelegramBotUsername()));
      sincronizarWebhook(config);
      tenantTelegramConfigRepository.save(config);
      response.success = true;
      response.telegramEnabled = true;
      response.message = "Conexao com o Telegram validada com sucesso.";
      registrarAuditoria(tenantId, "TELEGRAM_CONNECTION_TEST", Map.of("success", true), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.telegramEnabled = config.isTelegramEnabled();
      response.message = mapError(ex);
      registrarAuditoria(tenantId, "TELEGRAM_CONNECTION_TEST", Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  public TenantTelegramDtos.ValidateResponse validarConfiguracao(TenantTelegramDtos.ValidateRequest request) {
    TelegramBotClient.BotProfile profile = telegramBotClient.getMe(trimToNull(request.botToken));
    TenantTelegramDtos.ValidateResponse response = new TenantTelegramDtos.ValidateResponse();
    response.success = true;
    response.message = "Conexao com o Telegram validada com sucesso.";
    response.botUsername = profile.username;
    response.botDisplayName = profile.displayName;
    return response;
  }

  @Transactional
  public TenantTelegramDtos.TestMessageResponse enviarMensagemTeste(TenantTelegramDtos.TestMessageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    TenantTelegramConfig config = tenantTelegramConfigRepository.findByTenantIdOrCreate(tenantId);

    TenantTelegramDtos.TestMessageResponse response = new TenantTelegramDtos.TestMessageResponse();

    // O Bot API nao envia por telefone: chat_id e um id numerico (negativo para grupos)
    // ou @usuario. Rejeitar aqui evita uma ida ao Telegram e, principalmente, evita a
    // mensagem generica de "revise a configuracao" para um erro que e do destino.
    String destinationChatId = trimToNull(request.destinationChatId);
    if (destinationChatId != null && !CHAT_ID_PATTERN.matcher(destinationChatId).matches()) {
      response.success = false;
      response.message = INVALID_CHAT_ID_MESSAGE;
      registrarAuditoria(tenantId, "TELEGRAM_TEST_MESSAGE",
          Map.of("success", false, "error", "invalid_chat_id"), false);
      return response;
    }

    try {
      String providerMessageId = telegramBotClient.sendMessage(
          config,
          destinationChatId,
          firstNonBlank(trimToNull(request.message), "Mensagem de teste do AZZO Agenda Pro."));
      response.success = true;
      response.providerMessageId = providerMessageId;
      response.message = "Mensagem de teste enviada com sucesso.";
      registrarAuditoria(tenantId, "TELEGRAM_TEST_MESSAGE", Map.of("success", true), true);
      return response;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      response.success = false;
      response.message = mapSendError(ex);
      registrarAuditoria(tenantId, "TELEGRAM_TEST_MESSAGE", Map.of("success", false, "error", response.message), false);
      return response;
    }
  }

  /**
   * Erro de ENVIO nao e erro de configuracao: usar o {@link #mapError} generico aqui
   * apontava o usuario para "revise a configuracao" mesmo quando o token estava correto
   * e o problema era o destino (ex.: "chat not found" por telefone/ID inexistente, ou
   * pessoa que nunca iniciou conversa com o bot). Mantem a acao corretiva certa sem
   * vazar detalhe interno.
   */
  private String mapSendError(Exception ex) {
    String message = trimToNull(ex == null ? null : ex.getMessage());
    if (message == null) return "Falha ao enviar a mensagem de teste no Telegram.";
    String normalized = message.toLowerCase();
    if (normalized.contains("chat not found") || normalized.contains("chat_id")) {
      return INVALID_CHAT_ID_MESSAGE;
    }
    if (normalized.contains("bot was blocked") || normalized.contains("bot can't initiate")
        || normalized.contains("deactivated")) {
      return "Nao foi possivel enviar: a pessoa precisa iniciar uma conversa com o bot "
          + "(e nao te-lo bloqueado) antes de receber mensagens.";
    }
    if (normalized.contains("token") || normalized.contains("unauthorized")) {
      return "Falha ao enviar: revise o token do bot informado.";
    }
    return "Falha ao enviar a mensagem de teste no Telegram. Verifique o destino e tente novamente.";
  }

  @Transactional
  public TenantTelegramDtos.ConfigResponse obterConfiguracaoAtual() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    return toConfigResponse(tenantTelegramConfigRepository.findByTenantIdOrCreate(tenantId));
  }

  private TenantTelegramDtos.ConfigResponse toConfigResponse(TenantTelegramConfig config) {
    TenantTelegramDtos.ConfigResponse response = new TenantTelegramDtos.ConfigResponse();
    response.botUsername = trimToNull(config.getTelegramBotUsername());
    response.botTokenConfigured = hasBotTokenConfigured(config);
    response.webhookSecretTokenConfigured = trimToNull(config.getTelegramWebhookSecretTokenHash()) != null;
    response.webhookSecretToken = decrypt(config.getTelegramWebhookSecretTokenEnc());
    response.telegramEnabled = config.isTelegramEnabled();
    response.inboundWebhookUrl = buildWebhookUrl(config.getTenantId());
    response.webhook = resolveWebhookInfo(config);
    return response;
  }

  private TenantTelegramDtos.WebhookInfo resolveWebhookInfo(TenantTelegramConfig config) {
    if (!hasBotTokenConfigured(config)) return null;
    try {
      TelegramBotClient.WebhookInfo webhookInfo = telegramBotClient.getWebhookInfo(config);
      TenantTelegramDtos.WebhookInfo dto = new TenantTelegramDtos.WebhookInfo();
      dto.configured = webhookInfo.configured;
      dto.url = webhookInfo.url;
      dto.hasCustomCertificate = webhookInfo.hasCustomCertificate;
      dto.pendingUpdateCount = webhookInfo.pendingUpdateCount;
      dto.lastErrorDate = webhookInfo.lastErrorDate;
      dto.lastErrorMessage = webhookInfo.lastErrorMessage;
      dto.ipAddress = webhookInfo.ipAddress;
      return dto;
    } catch (RuntimeException ex) {
      TenantTelegramDtos.WebhookInfo dto = new TenantTelegramDtos.WebhookInfo();
      dto.configured = false;
      dto.lastErrorMessage = mapError(ex);
      return dto;
    }
  }

  private void ensureWebhookSecret(TenantTelegramConfig config) {
    if (trimToNull(config.getTelegramWebhookSecretTokenHash()) != null) return;
    assignWebhookSecret(config, generateSecretToken());
  }

  private void assignWebhookSecret(TenantTelegramConfig config, String rawToken) {
    config.setTelegramWebhookSecretTokenEnc(encryptionService.encrypt(rawToken));
    config.setTelegramWebhookSecretTokenHash(webhookVerifyTokenHashService.hash(rawToken));
  }

  private boolean hasBotTokenConfigured(TenantTelegramConfig config) {
    return trimToNull(decrypt(config.getTelegramBotTokenEnc())) != null;
  }

  private String buildWebhookUrl(UUID tenantId) {
    String base = trimToNull(publicBaseUrl);
    if (base == null || tenantId == null) return null;
    return stripTrailingSlash(base) + "/webhook/telegram/" + tenantId;
  }

  private void sincronizarWebhook(TenantTelegramConfig config) {
    if (config == null || !hasBotTokenConfigured(config)) return;
    if (!config.isTelegramEnabled()) {
      telegramBotClient.deleteWebhook(config);
      return;
    }

    String webhookUrl = buildWebhookUrl(config.getTenantId());
    if (webhookUrl == null) {
      throw new IllegalStateException("URL publica base nao configurada para registrar webhook do Telegram.");
    }
    ensureWebhookSecret(config);
    telegramBotClient.setWebhook(config, webhookUrl, decrypt(config.getTelegramWebhookSecretTokenEnc()));
  }

  private String generateSecretToken() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private String mapError(Exception ex) {
    String message = trimToNull(ex == null ? null : ex.getMessage());
    if (message == null) return "Falha ao validar a conexao com o Telegram.";
    if (message.toLowerCase().contains("token")) {
      return "Falha ao validar a conexao com o Telegram. Revise o token do bot informado.";
    }
    return "Falha ao validar a conexao com o Telegram. Revise a configuracao e tente novamente.";
  }

  private void registrarAuditoria(UUID tenantId, String acao, Object depois, boolean sucesso) {
    AuditEventCommand command = new AuditEventCommand();
    command.tenantId = tenantId;
    command.module = AuditConstants.Module.SETTINGS;
    command.action = acao;
    command.entityType = "TENANT";
    command.entityId = tenantId != null ? tenantId.toString() : null;
    command.after = depois;
    command.sourceChannel = AuditConstants.SourceChannel.API;
    if (sucesso) {
      auditService.recordSuccess(command);
    } else {
      auditService.recordError(command);
    }
  }

  private String decrypt(String encrypted) {
    String value = trimToNull(encrypted);
    if (value == null) return null;
    return trimToNull(encryptionService.decrypt(value));
  }

  private String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) return normalized;
    }
    return null;
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }
}
