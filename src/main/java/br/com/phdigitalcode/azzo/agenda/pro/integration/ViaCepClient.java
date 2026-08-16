package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Espelha {@code infrastructure/external/ViaCepClient.java}. Porte quase verbatim, trocando
 * {@code java.net.http.HttpClient} por {@code RestClient} do Spring (nunca WebClient), conforme
 * regra obrigatoria da migracao.
 */
@Component
public class ViaCepClient {

  private final RestClient restClient;

  public ViaCepClient() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(5));
    requestFactory.setReadTimeout(Duration.ofSeconds(10));
    this.restClient = RestClient.builder()
        .baseUrl("https://viacep.com.br")
        .requestFactory(requestFactory)
        .build();
  }

  public ViaCepAddress consultar(String cep) {
    try {
      JsonNode json = restClient.get()
          .uri("/ws/{cep}/json/", cep)
          .retrieve()
          .body(JsonNode.class);

      if (json == null) {
        throw new IllegalStateException("Falha ao consultar ViaCEP");
      }
      if (json.has("erro") && json.get("erro").asBoolean()) {
        throw new IllegalArgumentException("CEP nao encontrado");
      }

      ViaCepAddress payload = new ViaCepAddress();
      payload.cep = digitsOnly(json.path("cep").asText());
      payload.street = nullable(json.path("logradouro").asText());
      payload.complement = nullable(json.path("complemento").asText());
      payload.neighborhood = nullable(json.path("bairro").asText());
      payload.city = nullable(json.path("localidade").asText());
      payload.state = nullable(json.path("uf").asText());
      return payload;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao consultar ViaCEP", e);
    }
  }

  private String nullable(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String digitsOnly(String value) {
    return value == null ? null : value.replaceAll("\\D", "");
  }

  public static class ViaCepAddress {
    public String cep;
    public String street;
    public String complement;
    public String neighborhood;
    public String city;
    public String state;
  }
}
