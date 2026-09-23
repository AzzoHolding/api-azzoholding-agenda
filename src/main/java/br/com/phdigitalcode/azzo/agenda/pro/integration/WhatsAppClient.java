package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code infrastructure/messaging/WhatsAppClient.java} do Quarkus original. Porte trocando
 * {@code java.net.http.HttpClient} por {@code RestClient} do Spring (nunca WebClient), mesma regra
 * ja aplicada em {@code ViaCepClient}/{@code CnpjWsClient}/{@code AsaasClient}.
 *
 * <p>DIFERENCA DELIBERADA: o original lanca {@code io.quarkus.security.UnauthorizedException}
 * (classe do Quarkus, sem equivalente 1:1 em Spring) quando a Meta responde 401 e converte para
 * {@code IllegalStateException} com a mensagem "Token nao autorizado" antes de propagar — aqui o
 * 401 e detectado direto via {@link RestClientResponseException#getStatusCode()} e a mesma
 * {@code IllegalStateException} final e lancada, sem passar por um tipo intermediario de excecao de
 * seguranca (nao existe consumidor que capture o tipo Quarkus especificamente).
 */
@Component
public class WhatsAppClient {

  private static final Logger LOG = LoggerFactory.getLogger(WhatsAppClient.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final EncryptionService encryptionService;
  private final String graphApiVersion;

  @Autowired
  public WhatsAppClient(
      ObjectMapper objectMapper,
      EncryptionService encryptionService,
      @Value("${app.meta.graph-api-version:v18.0}") String graphApiVersion,
      @Value("${app.integration.whatsapp.connect-timeout-ms:10000}") long connectTimeoutMs,
      @Value("${app.integration.whatsapp.read-timeout-ms:20000}") long readTimeoutMs) {
    this.objectMapper = objectMapper;
    this.encryptionService = encryptionService;
    this.graphApiVersion = graphApiVersion == null || graphApiVersion.isBlank() ? "v18.0" : graphApiVersion.trim();
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /** Construtor de teste — injeta um {@link RestClient} ja configurado (ex.: MockRestServiceServer). */
  WhatsAppClient(RestClient restClient, ObjectMapper objectMapper, EncryptionService encryptionService, String graphApiVersion) {
    this.restClient = restClient;
    this.objectMapper = objectMapper;
    this.encryptionService = encryptionService;
    this.graphApiVersion = graphApiVersion == null || graphApiVersion.isBlank() ? "v18.0" : graphApiVersion.trim();
  }

  public String sendMessage(TenantWhatsAppConfig config, String to, String message) {
    String token = getTenantTokenOrFail(config);
    String phoneNumberId = getPhoneNumberIdOrFail(config);
    if (to == null || to.isBlank()) throw new IllegalArgumentException("Destino do WhatsApp invalido");
    if (message == null || message.isBlank()) throw new IllegalArgumentException("Mensagem vazia");

    String normalizedDestination = normalizeDestination(to);
    LOG.info(
        "whatsappClient.send.started {}",
        CorrelatedLogging.context(
            "tenantId", config.getTenantId(),
            "phoneNumberId", config.getWhatsappPhoneNumberId(),
            "destination", normalizedDestination));

    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("messaging_product", "whatsapp");
      payload.put("to", normalizedDestination);
      payload.put("type", "text");
      payload.put("text", Map.of("body", message));

      String body = restClient.post()
          .uri(graphApiBase() + phoneNumberId + "/messages")
          .header("Authorization", "Bearer " + token)
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(String.class);

      String providerMessageId = extractMessageId(body);
      LOG.info(
          "whatsappClient.send.completed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "phoneNumberId", config.getWhatsappPhoneNumberId(),
              "destination", normalizedDestination,
              "providerMessageId", providerMessageId));
      return providerMessageId;
    } catch (IllegalArgumentException e) {
      LOG.warn(
          "whatsappClient.send.invalidRequest {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "destination", to,
              "reason", safeMessage(e.getMessage())));
      throw e;
    } catch (RestClientResponseException e) {
      String errorMessage = extractErrorMessage(e.getResponseBodyAsString(), "Falha ao enviar mensagem no WhatsApp");
      if (e.getStatusCode().value() == 401) {
        LOG.error(
            "whatsappClient.send.unauthorized {}",
            CorrelatedLogging.context(
                "tenantId", config.getTenantId(),
                "phoneNumberId", config.getWhatsappPhoneNumberId(),
                "destination", to,
                "root", CorrelatedLogging.throwableSummary(e)),
            e);
        throw new IllegalStateException("Falha ao enviar mensagem no WhatsApp - Token nao autorizado: " + errorMessage, e);
      }
      LOG.error(
          "whatsappClient.send.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "phoneNumberId", config.getWhatsappPhoneNumberId(),
              "destination", to,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException(errorMessage, e);
    } catch (Exception e) {
      LOG.error(
          "whatsappClient.send.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "phoneNumberId", config.getWhatsappPhoneNumberId(),
              "destination", to,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException("Falha ao enviar mensagem no WhatsApp", e);
    }
  }

  /**
   * Envia um TEMPLATE aprovado.
   *
   * <p><b>E a unica forma de falar com quem nunca escreveu para o salao.</b> Texto livre so e
   * entregue dentro das 24h seguintes a ultima mensagem do cliente; fora dessa janela a Cloud API
   * <em>aceita, devolve o wamid e descarta</em> — a mensagem nunca chega, e a falha so aparece
   * como status de webhook (131047). Foi o que aconteceu em producao em 2026-09-22: tres envios de
   * teste com wamid valido, nenhum entregue, e o log dizendo "Entregue".
   *
   * <p>Primeiro contato — confirmacao de agendamento de cliente novo — cai sempre nesse caso.
   */
  public String enviarTemplate(
      TenantWhatsAppConfig config, String to, String templateName, String languageCode) {
    return enviarTemplate(config, to, templateName, languageCode, List.of());
  }

  /**
   * O mesmo envio, com as VARIAVEIS do corpo do template.
   *
   * <p>Uma confirmacao de agendamento nao e um texto fixo: ela diz o nome do cliente, o servico, o
   * dia e a hora. No template aprovado esses pedacos sao {@code {{1}}, {{2}}...}, e a ordem desta
   * lista e a ordem deles — trocar duas posicoes manda o servico no lugar do nome, sem erro
   * nenhum da Meta.
   */
  public String enviarTemplate(
      TenantWhatsAppConfig config,
      String to,
      String templateName,
      String languageCode,
      List<String> variaveis) {
    String token = getTenantTokenOrFail(config);
    String phoneNumberId = getPhoneNumberIdOrFail(config);
    if (to == null || to.isBlank()) throw new IllegalArgumentException("Destino do WhatsApp invalido");
    if (templateName == null || templateName.isBlank()) {
      throw new IllegalArgumentException("Nome do template do WhatsApp nao informado");
    }
    String idioma = languageCode == null || languageCode.isBlank() ? "en_US" : languageCode.trim();

    String normalizedDestination = normalizeDestination(to);
    LOG.info(
        "whatsappClient.sendTemplate.started {}",
        CorrelatedLogging.context(
            "tenantId", config.getTenantId(),
            "phoneNumberId", phoneNumberId,
            "destination", normalizedDestination,
            "template", templateName));

    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("messaging_product", "whatsapp");
      payload.put("to", normalizedDestination);
      payload.put("type", "template");
      Map<String, Object> template = new HashMap<>();
      template.put("name", templateName);
      template.put("language", Map.of("code", idioma));
      if (variaveis != null && !variaveis.isEmpty()) {
        // "body" com parametros de texto, na ordem de {{1}}, {{2}}... Nulo viraria "null" no
        // corpo da mensagem que o cliente le, entao vira vazio.
        List<Map<String, String>> parametros =
            variaveis.stream()
                .map(valor -> Map.of("type", "text", "text", valor == null ? "" : valor))
                .toList();
        template.put("components", List.of(Map.of("type", "body", "parameters", parametros)));
      }
      payload.put("template", template);

      String body = restClient.post()
          .uri(graphApiBase() + phoneNumberId + "/messages")
          .header("Authorization", "Bearer " + token)
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(String.class);

      String providerMessageId = extractMessageId(body);
      LOG.info(
          "whatsappClient.sendTemplate.completed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "destination", normalizedDestination,
              "template", templateName,
              "providerMessageId", providerMessageId));
      return providerMessageId;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (RestClientResponseException e) {
      String errorMessage =
          extractErrorMessage(e.getResponseBodyAsString(), "Falha ao enviar o template no WhatsApp");
      LOG.error(
          "whatsappClient.sendTemplate.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "destination", to,
              "template", templateName,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException(errorMessage, e);
    } catch (Exception e) {
      LOG.error(
          "whatsappClient.sendTemplate.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(), "destination", to, "template", templateName),
          e);
      throw new IllegalStateException("Falha ao enviar o template no WhatsApp", e);
    }
  }

  /**
   * Registra o numero no Cloud API. <b>Sem isto o numero nao envia mensagem nenhuma.</b>
   *
   * <p>O numero pode estar verificado, aparecer com o nome certo e responder a toda consulta de
   * leitura — e ainda assim recusar todo envio com {@code (#133010) Account not registered}. Foi o
   * que aconteceu em producao com o numero do Azzo: o onboarding dizia "conectado" porque validava
   * com uma LEITURA, e leitura funciona em numero mudo.
   *
   * <p><b>Idempotente de proposito.</b> Registrar duas vezes nao e erro, e a Meta responde de
   * formas diferentes para "ja estava registrado" — tratar isso como falha faria o onboarding
   * quebrar em toda reconexao de um numero que ja funcionava.
   *
   * @return {@code true} quando o numero ficou registrado, inclusive se ja estava.
   */
  public boolean registrarNumero(TenantWhatsAppConfig config, String pin) {
    String token = getTenantTokenOrFail(config);
    String phoneNumberId = getPhoneNumberIdOrFail(config);
    if (pin == null || !pin.matches("[0-9]{6}")) {
      throw new IllegalArgumentException("PIN do registro deve ter exatamente 6 digitos");
    }

    LOG.info(
        "whatsappClient.register.started {}",
        CorrelatedLogging.context("tenantId", config.getTenantId(), "phoneNumberId", phoneNumberId));

    try {
      restClient.post()
          .uri(graphApiBase() + phoneNumberId + "/register")
          .header("Authorization", "Bearer " + token)
          .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
          .body(Map.of("messaging_product", "whatsapp", "pin", pin))
          .retrieve()
          .body(String.class);
      LOG.info(
          "whatsappClient.register.completed {}",
          CorrelatedLogging.context("tenantId", config.getTenantId(), "phoneNumberId", phoneNumberId));
      return true;
    } catch (RestClientResponseException e) {
      String corpo = e.getResponseBodyAsString();
      if (jaRegistrado(corpo)) {
        LOG.info(
            "whatsappClient.register.alreadyDone {}",
            CorrelatedLogging.context("tenantId", config.getTenantId(), "phoneNumberId", phoneNumberId));
        return true;
      }
      String errorMessage = extractErrorMessage(corpo, "Falha ao registrar o numero no WhatsApp Cloud API");
      LOG.error(
          "whatsappClient.register.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "phoneNumberId", phoneNumberId,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException(errorMessage, e);
    } catch (Exception e) {
      LOG.error(
          "whatsappClient.register.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "phoneNumberId", phoneNumberId,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException("Falha ao registrar o numero no WhatsApp Cloud API", e);
    }
  }

  /**
   * "Ja estava registrado" nao e falha.
   *
   * <p>A Meta sinaliza isso de mais de uma forma dependendo do estado do numero, e nenhuma delas
   * significa que o onboarding deu errado.
   */
  private boolean jaRegistrado(String corpo) {
    if (corpo == null) return false;
    String texto = corpo.toLowerCase();
    return texto.contains("already registered")
        || texto.contains("already been registered")
        || texto.contains("133005")
        || texto.contains("133006");
  }

  /**
   * Inscreve o app do Azzo no webhook da conta comercial do cliente.
   *
   * <p><b>Sem isto o salao envia mas nao RECEBE.</b> Nada do que o cliente responder chega ao
   * Azzo — nem confirmacao, nem cancelamento, nem o agendamento pelo assistente. O verify token e
   * gerado no onboarding; esta inscricao, que e o outro lado da mesma ponte, nunca era feita.
   */
  public boolean inscreverNoWebhook(TenantWhatsAppConfig config) {
    String token = getTenantTokenOrFail(config);
    String wabaId = config == null ? null : config.getWhatsappBusinessAccountId();
    if (wabaId == null || wabaId.isBlank()) {
      throw new IllegalArgumentException("Conta comercial do WhatsApp nao informada para o tenant");
    }

    LOG.info(
        "whatsappClient.subscribeApp.started {}",
        CorrelatedLogging.context("tenantId", config.getTenantId(), "wabaId", wabaId));

    try {
      restClient.post()
          .uri(graphApiBase() + wabaId + "/subscribed_apps")
          .header("Authorization", "Bearer " + token)
          .retrieve()
          .body(String.class);
      LOG.info(
          "whatsappClient.subscribeApp.completed {}",
          CorrelatedLogging.context("tenantId", config.getTenantId(), "wabaId", wabaId));
      return true;
    } catch (RestClientResponseException e) {
      String errorMessage =
          extractErrorMessage(e.getResponseBodyAsString(), "Falha ao inscrever o webhook na conta do WhatsApp");
      LOG.error(
          "whatsappClient.subscribeApp.failed {}",
          CorrelatedLogging.context(
              "tenantId", config.getTenantId(),
              "wabaId", wabaId,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException(errorMessage, e);
    } catch (Exception e) {
      LOG.error(
          "whatsappClient.subscribeApp.failed {}",
          CorrelatedLogging.context("tenantId", config.getTenantId(), "wabaId", wabaId),
          e);
      throw new IllegalStateException("Falha ao inscrever o webhook na conta do WhatsApp", e);
    }
  }

  public boolean testConnection(TenantWhatsAppConfig config) {
    fetchPhoneNumberDetails(config);
    return true;
  }

  public PhoneNumberDetails fetchPhoneNumberDetails(TenantWhatsAppConfig config) {
    String token = getTenantTokenOrFail(config);
    String phoneNumberId = getPhoneNumberIdOrFail(config);
    return fetchPhoneNumberDetails(token, phoneNumberId);
  }

  public PhoneNumberDetails fetchPhoneNumberDetails(String accessToken, String phoneNumberId) {
    String token = accessToken == null ? null : accessToken.trim();
    String normalizedPhoneNumberId = phoneNumberId == null ? null : phoneNumberId.trim();
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Token do WhatsApp nao configurado para o tenant");
    }
    if (normalizedPhoneNumberId == null || normalizedPhoneNumberId.isBlank()) {
      throw new IllegalArgumentException("phoneNumberId do WhatsApp nao configurado para o tenant");
    }

    LOG.info("whatsappClient.fetchPhoneNumberDetails.started {}", CorrelatedLogging.context("phoneNumberId", normalizedPhoneNumberId));

    try {
      String body = restClient.get()
          .uri(graphApiBase() + normalizedPhoneNumberId)
          .header("Authorization", "Bearer " + token)
          .retrieve()
          .body(String.class);

      JsonNode payload = objectMapper.readTree(body);
      PhoneNumberDetails details = new PhoneNumberDetails();
      details.id = text(payload.path("id"));
      details.displayPhoneNumber = text(payload.path("display_phone_number"));
      details.verifiedName = text(payload.path("verified_name"));
      if (details.id == null || details.id.isBlank()) {
        throw new IllegalStateException("Meta nao retornou dados validos para o Phone Number ID informado.");
      }

      LOG.info(
          "whatsappClient.fetchPhoneNumberDetails.completed {}",
          CorrelatedLogging.context("phoneNumberId", normalizedPhoneNumberId, "verifiedName", details.verifiedName));
      return details;
    } catch (IllegalArgumentException e) {
      LOG.warn(
          "whatsappClient.fetchPhoneNumberDetails.invalidRequest {}",
          CorrelatedLogging.context("phoneNumberId", normalizedPhoneNumberId, "reason", safeMessage(e.getMessage())));
      throw e;
    } catch (IllegalStateException e) {
      LOG.error(
          "whatsappClient.fetchPhoneNumberDetails.failed {}",
          CorrelatedLogging.context("phoneNumberId", normalizedPhoneNumberId, "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw e;
    } catch (RestClientResponseException e) {
      String errorMessage = extractErrorMessage(e.getResponseBodyAsString(), "Falha ao validar configuracao do WhatsApp");
      LOG.error(
          "whatsappClient.fetchPhoneNumberDetails.failed {}",
          CorrelatedLogging.context("phoneNumberId", normalizedPhoneNumberId, "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException(errorMessage, e);
    } catch (Exception e) {
      LOG.error(
          "whatsappClient.fetchPhoneNumberDetails.unexpected {}",
          CorrelatedLogging.context("phoneNumberId", normalizedPhoneNumberId, "root", CorrelatedLogging.throwableSummary(e)),
          e);
      throw new IllegalStateException("Falha ao validar configuracao do WhatsApp", e);
    }
  }

  private String getTenantTokenOrFail(TenantWhatsAppConfig config) {
    if (config == null) throw new IllegalArgumentException("Configuracao de WhatsApp invalida");
    String encrypted = config.getWhatsappAccessTokenEnc();
    if (encrypted == null || encrypted.isBlank()) {
      throw new IllegalArgumentException("Token do WhatsApp nao configurado para o tenant");
    }
    String token = encryptionService.decrypt(encrypted);
    if (token.isBlank()) throw new IllegalArgumentException("Token do WhatsApp nao configurado para o tenant");
    return token;
  }

  private String getPhoneNumberIdOrFail(TenantWhatsAppConfig config) {
    if (config.getWhatsappPhoneNumberId() == null || config.getWhatsappPhoneNumberId().isBlank()) {
      throw new IllegalArgumentException("phoneNumberId do WhatsApp nao configurado para o tenant");
    }
    return config.getWhatsappPhoneNumberId();
  }

  private String normalizeDestination(String destination) {
    return destination.replaceAll("\\D", "");
  }

  private String extractErrorMessage(String responseBody, String defaultMessage) {
    try {
      JsonNode json = objectMapper.readTree(responseBody);
      JsonNode errorNode = json.path("error").path("message");
      if (errorNode.isTextual() && !errorNode.asText().isBlank()) {
        return errorNode.asText();
      }
      return defaultMessage;
    } catch (Exception e) {
      return defaultMessage;
    }
  }

  private String extractMessageId(String responseBody) {
    try {
      JsonNode json = objectMapper.readTree(responseBody);
      JsonNode messages = json.path("messages");
      JsonNode first = messages.isArray() && !messages.isEmpty() ? messages.get(0) : null;
      if (first == null) return null;
      String id = first.path("id").asText(null);
      return id == null || id.isBlank() ? null : id;
    } catch (Exception e) {
      return null;
    }
  }

  private String graphApiBase() {
    return "https://graph.facebook.com/" + graphApiVersion + "/";
  }

  private String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    String value = node.asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String safeMessage(String value) {
    if (value == null || value.isBlank()) return "n/a";
    return value.length() > 220 ? value.substring(0, 220) : value;
  }

  public static class PhoneNumberDetails {
    public String id;
    public String displayPhoneNumber;
    public String verifiedName;
  }
}
