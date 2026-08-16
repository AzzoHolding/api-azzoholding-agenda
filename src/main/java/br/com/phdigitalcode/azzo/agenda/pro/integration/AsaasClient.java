package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Espelha {@code infrastructure/payment/AsaasClient.java} — interface declarativa
 * {@code @RegisterRestClient(configKey = "asaas-api")} do MicroProfile virou uma classe com
 * {@link RestClient} do Spring (nunca WebClient). Mesmo path base {@code /v3}, mesmo header de
 * autenticacao {@code access_token} por chamada (a chave e do TENANT, nao da plataforma, entao nao
 * pode ser fixada no builder).
 *
 * <p>Timeouts replicam {@code quarkus.rest-client.asaas-api.connect-timeout=10000} /
 * {@code read-timeout=20000}.
 */
@Component
public class AsaasClient {

  private static final String ACCESS_TOKEN_HEADER = "access_token";

  private final RestClient restClient;

  public AsaasClient(
      @Value("${app.asaas.base-url:https://sandbox.asaas.com}") String baseUrl,
      @Value("${app.asaas.connect-timeout-ms:10000}") long connectTimeoutMs,
      @Value("${app.asaas.read-timeout-ms:20000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient =
        RestClient.builder().baseUrl(baseUrl + "/v3").requestFactory(requestFactory).build();
  }

  public AsaasDtos.CustomerResponse createCustomer(
      String apiKey, AsaasDtos.CreateCustomerRequest request) {
    return restClient
        .post()
        .uri("/customers")
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .body(request)
        .retrieve()
        .body(AsaasDtos.CustomerResponse.class);
  }

  public AsaasDtos.SubscriptionResponse createSubscription(
      String apiKey, AsaasDtos.CreateSubscriptionRequest request) {
    return restClient
        .post()
        .uri("/subscriptions")
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .body(request)
        .retrieve()
        .body(AsaasDtos.SubscriptionResponse.class);
  }

  public AsaasDtos.PaymentResponse createPayment(
      String apiKey, AsaasDtos.CreatePaymentRequest request) {
    return restClient
        .post()
        .uri("/payments")
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .body(request)
        .retrieve()
        .body(AsaasDtos.PaymentResponse.class);
  }

  public AsaasDtos.PaymentsResponse listPayments(
      String apiKey, String subscriptionId, Integer limit, Integer offset) {
    return restClient
        .get()
        .uri(
            uriBuilder -> {
              uriBuilder.path("/payments");
              if (subscriptionId != null) uriBuilder.queryParam("subscription", subscriptionId);
              if (limit != null) uriBuilder.queryParam("limit", limit);
              if (offset != null) uriBuilder.queryParam("offset", offset);
              return uriBuilder.build();
            })
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .retrieve()
        .body(AsaasDtos.PaymentsResponse.class);
  }

  public AsaasDtos.PixQrCodeResponse getPixQrCode(String apiKey, String paymentId) {
    return restClient
        .get()
        .uri("/payments/{id}/pixQrCode", paymentId)
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .retrieve()
        .body(AsaasDtos.PixQrCodeResponse.class);
  }

  public AsaasDtos.IdentificationFieldResponse getIdentificationField(
      String apiKey, String paymentId) {
    return restClient
        .get()
        .uri("/payments/{id}/identificationField", paymentId)
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .retrieve()
        .body(AsaasDtos.IdentificationFieldResponse.class);
  }

  public void cancelPayment(String apiKey, String paymentId) {
    restClient
        .delete()
        .uri("/payments/{id}", paymentId)
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .retrieve()
        .toBodilessEntity();
  }

  public void cancelSubscription(String apiKey, String subscriptionId) {
    restClient
        .delete()
        .uri("/subscriptions/{id}", subscriptionId)
        .header(ACCESS_TOKEN_HEADER, apiKey)
        .retrieve()
        .toBodilessEntity();
  }
}
