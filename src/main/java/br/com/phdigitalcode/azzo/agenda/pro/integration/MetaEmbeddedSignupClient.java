package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Espelha {@code modules/tenant/infrastructure/MetaEmbeddedSignupClient.java} do Quarkus original.
 * Porte trocando {@code java.net.http.HttpClient} por {@code RestClient} do Spring, mesma regra ja
 * aplicada em {@code WhatsAppClient}/{@code TelegramBotClient}.
 */
@Component
public class MetaEmbeddedSignupClient implements MetaEmbeddedSignupGateway {

  private static final String GRAPH_BASE = "https://graph.facebook.com/";

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final String appId;
  private final String appSecret;
  private final String graphApiVersion;

  @Autowired
  public MetaEmbeddedSignupClient(
      ObjectMapper objectMapper,
      @Value("${app.meta.app-id}") String appId,
      @Value("${app.meta.app-secret}") String appSecret,
      @Value("${app.meta.graph-api-version}") String graphApiVersion,
      @Value("${app.integration.whatsapp.connect-timeout-ms:10000}") long connectTimeoutMs,
      @Value("${app.integration.whatsapp.read-timeout-ms:20000}") long readTimeoutMs) {
    this.objectMapper = objectMapper;
    this.appId = appId;
    this.appSecret = appSecret;
    this.graphApiVersion = graphApiVersion;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /** Construtor de teste — injeta um {@link RestClient} ja configurado (ex.: MockRestServiceServer). */
  MetaEmbeddedSignupClient(RestClient restClient, ObjectMapper objectMapper, String appId, String appSecret, String graphApiVersion) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.appId = appId;
    this.appSecret = appSecret;
    this.graphApiVersion = graphApiVersion;
  }

  @Override
  public String exchangeCodeForAccessToken(String code) {
    String normalizedCode = required(code, "Code do Embedded Signup ausente.");
    ensureMetaCredentialsConfigured();

    String uri = GRAPH_BASE + graphApiVersion + "/oauth/access_token"
        + "?client_id=" + encode(appId)
        + "&client_secret=" + encode(appSecret)
        + "&code=" + encode(normalizedCode);

    JsonNode payload = sendGet(uri, null, "Falha ao trocar code do Embedded Signup por access token.");
    String accessToken = text(payload.path("access_token"));
    if (accessToken == null) {
      throw new IllegalStateException("Meta nao retornou access token no code exchange.");
    }
    return accessToken;
  }

  @Override
  public PhoneNumberDetails fetchPhoneNumberDetails(String accessToken, String phoneNumberId) {
    String normalizedPhoneNumberId = required(phoneNumberId, "Phone Number ID ausente no Embedded Signup.");
    String uri = GRAPH_BASE + graphApiVersion + "/" + encodePath(normalizedPhoneNumberId)
        + "?fields=id,display_phone_number,verified_name";

    JsonNode payload = sendGet(uri, required(accessToken, "Access token da Meta ausente."),
        "Falha ao consultar Phone Number ID na Meta.");

    PhoneNumberDetails details = new PhoneNumberDetails();
    details.id = text(payload.path("id"));
    details.displayPhoneNumber = text(payload.path("display_phone_number"));
    details.verifiedName = text(payload.path("verified_name"));
    if (details.id == null) {
      throw new IllegalStateException("Meta nao retornou id valido para o Phone Number ID informado.");
    }
    return details;
  }

  private JsonNode sendGet(String uri, String accessToken, String defaultErrorMessage) {
    try {
      RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(uri);
      if (accessToken != null && !accessToken.isBlank()) {
        spec = spec.header("Authorization", "Bearer " + accessToken);
      }
      String body = spec.retrieve().body(String.class);
      return parseJson(body);
    } catch (RestClientResponseException e) {
      JsonNode payload = tryParseJson(e.getResponseBodyAsString());
      throw new IllegalStateException(extractErrorMessage(payload, defaultErrorMessage), e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(defaultErrorMessage, e);
    }
  }

  private JsonNode parseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao interpretar resposta JSON da Meta.", e);
    }
  }

  private JsonNode tryParseJson(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      return null;
    }
  }

  private String extractErrorMessage(JsonNode payload, String defaultMessage) {
    if (payload == null) return defaultMessage;
    String message = text(payload.path("error").path("message"));
    String code = text(payload.path("error").path("code"));
    if (message == null) return defaultMessage;
    return code == null ? message : message + " (code=" + code + ")";
  }

  private void ensureMetaCredentialsConfigured() {
    if (isUnset(appId) || isUnset(appSecret)) {
      throw new IllegalStateException("META_APP_ID/META_APP_SECRET nao configurados.");
    }
  }

  private boolean isUnset(String value) {
    return value == null || value.isBlank() || "__unset__".equalsIgnoreCase(value.trim());
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String encodePath(String value) {
    return value.replaceAll("[^A-Za-z0-9_-]", "");
  }

  private String required(String value, String message) {
    String normalized = textNode(value);
    if (normalized == null) throw new IllegalStateException(message);
    return normalized;
  }

  private String text(JsonNode node) {
    if (node == null || node.isNull()) return null;
    return textNode(node.asText());
  }

  private String textNode(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static class PhoneNumberDetails {
    public String id;
    public String displayPhoneNumber;
    public String verifiedName;
  }
}
