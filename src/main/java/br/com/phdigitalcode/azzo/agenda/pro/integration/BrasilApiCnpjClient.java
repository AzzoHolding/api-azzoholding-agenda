package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Espelha {@code modules/nfse/infrastructure/client/BrasilApiCnpjClient.java} — usado aqui apenas
 * como fallback do {@code CnpjConsultaService} (o modulo {@code nfse} completo nao foi migrado).
 */
@Component
public class BrasilApiCnpjClient {

  private final RestClient restClient;

  public BrasilApiCnpjClient(
      @Value("${app.integration.brasilapi-cnpj.base-url:https://brasilapi.com.br}") String baseUrl,
      @Value("${app.integration.brasilapi-cnpj.connect-timeout-ms:5000}") long connectTimeoutMs,
      @Value("${app.integration.brasilapi-cnpj.read-timeout-ms:8000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  public BrasilApiCnpjResponse lookup(String cnpj) {
    return restClient.get().uri("/api/cnpj/v1/{cnpj}", cnpj).retrieve().body(BrasilApiCnpjResponse.class);
  }

  public static class BrasilApiCnpjResponse {
    public String cnpj;
    public String razao_social;
    public String nome_fantasia;
    public String email;
    public String ddd_telefone_1;
    public String descricao_situacao_cadastral;
    public String descricao_tipo_de_logradouro;
    public String logradouro;
    public String numero;
    public String complemento;
    public String bairro;
    public String municipio;
    public String uf;
    public String cep;
  }
}
