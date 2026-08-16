package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantReactivationSeedRequest;

/**
 * REST client para o azzo-assistant-api. Espelha {@code infrastructure/client/AssistantApiClient
 * .java} (interface {@code @RegisterRestClient(configKey = "assistant-api")} do MicroProfile REST
 * Client) + {@code AssistantApiKeyHeaderFactory} (header {@code X-Internal-Api-Key} injetado em
 * toda chamada, autenticacao service-to-service) — aqui unificados numa unica classe com
 * {@link RestClient} do Spring (nunca WebClient), header fixado no builder ja que a chave e da
 * plataforma (nao varia por tenant, ao contrario do Asaas).
 *
 * <p>Usado pelo {@code WhatsAppWebhookResource}/{@code TelegramWebhookResource} para processar
 * mensagens recebidas via assistant (fronteiras seguintes de {@code chat}, ainda nao portadas).
 */
@Component
public class AssistantApiClient {

  private final RestClient restClient;

  @Autowired
  public AssistantApiClient(
      @Value("${app.integration.assistant-api.base-url:http://localhost:8081}") String baseUrl,
      @Value("${app.internal.api-key:changeme-dev}") String internalApiKey,
      @Value("${app.integration.assistant-api.connect-timeout-ms:5000}") long connectTimeoutMs,
      @Value("${app.integration.assistant-api.read-timeout-ms:15000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory);
    if (internalApiKey != null && !internalApiKey.isBlank()) {
      builder.defaultHeader("X-Internal-Api-Key", internalApiKey);
    }
    this.restClient = builder.build();
  }

  /** Construtor de teste — injeta um {@link RestClient} ja configurado (ex.: MockRestServiceServer). */
  AssistantApiClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public AssistantMessageResponse processarMensagem(
      String tenantId, String userIdentifier, String userName, AssistantMessageRequest request) {
    return restClient.post()
        .uri("/api/v1/assistant/message")
        .header("X-Tenant-Id", tenantId)
        .header("X-User-Identifier", userIdentifier)
        .header("X-User-Name", userName)
        .body(request)
        .retrieve()
        .body(AssistantMessageResponse.class);
  }

  /**
   * Invalida o cache do system prompt para o tenant informado. Deve ser chamado apos alterar
   * horarios, servicos ou profissionais.
   */
  public void invalidarCachePrompt(String tenantId) {
    restClient.delete()
        .uri(uriBuilder -> uriBuilder.path("/api/v1/assistant/admin/cache").queryParam("tenantId", tenantId).build())
        .retrieve()
        .toBodilessEntity();
  }

  /**
   * Pre-semeia um contexto de confirmacao de presenca para o cliente. Deve ser chamado
   * imediatamente apos enviar o lembrete automatico de agendamento, para que a resposta do cliente
   * seja interpretada corretamente pelo assistente.
   */
  public void seedReminderContext(String tenantId, String userIdentifier, String appointmentId, String customerName) {
    restClient.post()
        .uri(uriBuilder -> uriBuilder.path("/api/v1/assistant/admin/seed-reminder")
            .queryParam("tenantId", tenantId)
            .queryParam("userIdentifier", userIdentifier)
            .queryParam("appointmentId", appointmentId)
            .queryParam("customerName", customerName)
            .build())
        .retrieve()
        .toBodilessEntity();
  }

  public void seedReactivationContext(String tenantId, String userIdentifier, AssistantReactivationSeedRequest request) {
    restClient.post()
        .uri(uriBuilder -> uriBuilder.path("/api/v1/assistant/admin/seed-reactivation")
            .queryParam("tenantId", tenantId)
            .queryParam("userIdentifier", userIdentifier)
            .build())
        .body(request)
        .retrieve()
        .toBodilessEntity();
  }
}
