package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code infrastructure/messaging/TelegramBotClient.java} do Quarkus original. Porte
 * trocando {@code java.net.http.HttpClient} por {@code RestClient} do Spring, mesma regra ja
 * aplicada em {@code WhatsAppClient}/{@code ViaCepClient}/{@code CnpjWsClient}.
 */
@Component
public class TelegramBotClient {

  private static final Logger LOG = LoggerFactory.getLogger(TelegramBotClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final EncryptionService encryptionService;

  @Autowired
  public TelegramBotClient(
      ObjectMapper objectMapper,
      EncryptionService encryptionService,
      @Value("${app.integration.telegram.connect-timeout-ms:10000}") long connectTimeoutMs,
      @Value("${app.integration.telegram.read-timeout-ms:20000}") long readTimeoutMs) {
    this.objectMapper = objectMapper;
    this.encryptionService = encryptionService;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /** Construtor de teste — injeta um {@link RestClient} ja configurado (ex.: MockRestServiceServer). */
  TelegramBotClient(RestClient restClient, ObjectMapper objectMapper, EncryptionService encryptionService) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.encryptionService = encryptionService;
  }

  public BotProfile getMe(TenantTelegramConfig config) {
    if (config == null) throw new IllegalArgumentException("Configuracao do Telegram invalida.");
    return getMe(resolveBotToken(config));
  }

  public BotProfile getMe(String botToken) {
    String token = normalize(botToken);
    if (token == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");

    LOG.info("telegramClient.getMe.started {}", CorrelatedLogging.context());
    JsonNode payload = execute(token, "getMe", null);
    JsonNode result = payload.path("result");

    BotProfile profile = new BotProfile();
    profile.id = result.path("id").asText(null);
    profile.username = normalize(result.path("username").asText(null));
    profile.displayName = firstNonBlank(
        normalize(result.path("first_name").asText(null)),
        profile.username);
    return profile;
  }

  public String sendMessage(TenantTelegramConfig config, String chatId, String message) {
    if (config == null) throw new IllegalArgumentException("Configuracao do Telegram invalida.");
    return sendMessage(resolveBotToken(config), chatId, message);
  }

  public void setWebhook(TenantTelegramConfig config, String webhookUrl, String secretToken) {
    if (config == null) throw new IllegalArgumentException("Configuracao do Telegram invalida.");
    setWebhook(resolveBotToken(config), webhookUrl, secretToken);
  }

  public void setWebhook(String botToken, String webhookUrl, String secretToken) {
    String token = normalize(botToken);
    String normalizedWebhookUrl = normalize(webhookUrl);
    String normalizedSecretToken = normalize(secretToken);

    if (token == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");
    if (normalizedWebhookUrl == null) throw new IllegalArgumentException("URL do webhook do Telegram invalida.");

    LOG.info("telegramClient.setWebhook.started {}", CorrelatedLogging.context("webhookUrl", normalizedWebhookUrl));

    execute(token, "setWebhook", normalizedSecretToken == null
        ? Map.of("url", normalizedWebhookUrl)
        : Map.of("url", normalizedWebhookUrl, "secret_token", normalizedSecretToken));

    LOG.info("telegramClient.setWebhook.completed {}", CorrelatedLogging.context("webhookUrl", normalizedWebhookUrl));
  }

  public void deleteWebhook(TenantTelegramConfig config) {
    if (config == null) throw new IllegalArgumentException("Configuracao do Telegram invalida.");
    deleteWebhook(resolveBotToken(config));
  }

  public void deleteWebhook(String botToken) {
    String token = normalize(botToken);
    if (token == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");

    LOG.info("telegramClient.deleteWebhook.started {}", CorrelatedLogging.context());
    execute(token, "deleteWebhook", Map.of("drop_pending_updates", Boolean.FALSE));
    LOG.info("telegramClient.deleteWebhook.completed {}", CorrelatedLogging.context());
  }

  public WebhookInfo getWebhookInfo(TenantTelegramConfig config) {
    if (config == null) throw new IllegalArgumentException("Configuracao do Telegram invalida.");
    return getWebhookInfo(resolveBotToken(config));
  }

  public WebhookInfo getWebhookInfo(String botToken) {
    String token = normalize(botToken);
    if (token == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");

    LOG.info("telegramClient.getWebhookInfo.started {}", CorrelatedLogging.context());
    JsonNode payload = execute(token, "getWebhookInfo", null);
    JsonNode result = payload.path("result");

    WebhookInfo info = new WebhookInfo();
    info.url = normalize(result.path("url").asText(null));
    info.hasCustomCertificate = result.path("has_custom_certificate").asBoolean(false);
    info.pendingUpdateCount = result.path("pending_update_count").asInt(0);
    info.lastErrorDate = result.path("last_error_date").isMissingNode() || result.path("last_error_date").isNull()
        ? null
        : result.path("last_error_date").asText();
    info.lastErrorMessage = normalize(result.path("last_error_message").asText(null));
    info.ipAddress = normalize(result.path("ip_address").asText(null));
    info.configured = info.url != null;
    return info;
  }

  public String sendMessage(String botToken, String chatId, String message) {
    String token = normalize(botToken);
    String normalizedChatId = normalize(chatId);
    String normalizedMessage = normalize(message);

    if (token == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");
    if (normalizedChatId == null) throw new IllegalArgumentException("Destino do Telegram invalido.");
    if (normalizedMessage == null) throw new IllegalArgumentException("Mensagem do Telegram vazia.");

    LOG.info("telegramClient.send.started {}", CorrelatedLogging.context("destination", normalizedChatId));

    JsonNode payload = execute(token, "sendMessage", Map.of(
        "chat_id", normalizedChatId,
        "text", normalizedMessage));
    String providerMessageId = payload.path("result").path("message_id").asText(null);

    LOG.info(
        "telegramClient.send.completed {}",
        CorrelatedLogging.context("destination", normalizedChatId, "providerMessageId", providerMessageId));
    return providerMessageId;
  }

  private JsonNode execute(String botToken, String method, Object body) {
    try {
      String uri = "https://api.telegram.org/bot" + botToken + "/" + method;
      String responseBody;
      if (body == null) {
        responseBody = restClient.get().uri(uri).retrieve().body(String.class);
      } else {
        responseBody = restClient.post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
      }
      JsonNode payload = objectMapper.readTree(responseBody);
      if (!payload.path("ok").asBoolean(false)) {
        throw new IllegalStateException(extractError(payload, "Falha ao comunicar com o Telegram."));
      }
      return payload;
    } catch (RestClientResponseException e) {
      JsonNode payload = tryReadTree(e.getResponseBodyAsString());
      String message = payload == null ? "Falha ao comunicar com o Telegram." : extractError(payload, "Falha ao comunicar com o Telegram.");
      throw new IllegalStateException(message, e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao comunicar com o Telegram.", e);
    }
  }

  private JsonNode tryReadTree(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      return null;
    }
  }

  private String resolveBotToken(TenantTelegramConfig config) {
    String encrypted = normalize(config.getTelegramBotTokenEnc());
    if (encrypted == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");
    String token = normalize(encryptionService.decrypt(encrypted));
    if (token == null) throw new IllegalArgumentException("Token do bot do Telegram nao configurado.");
    return token;
  }

  private String extractError(JsonNode payload, String fallback) {
    String description = normalize(payload.path("description").asText(null));
    return description != null ? description : fallback;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = normalize(value);
      if (normalized != null) return normalized;
    }
    return null;
  }

  private String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  public static class BotProfile {
    public String id;
    public String username;
    public String displayName;
  }

  public static class WebhookInfo {
    public boolean configured;
    public String url;
    public boolean hasCustomCertificate;
    public int pendingUpdateCount;
    public String lastErrorDate;
    public String lastErrorMessage;
    public String ipAddress;
  }
}
