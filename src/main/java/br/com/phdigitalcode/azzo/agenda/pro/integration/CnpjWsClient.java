package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Espelha {@code modules/company/infrastructure/client/CnpjWsClient.java} ({@code @RegisterRestClient}
 * do MicroProfile REST Client -> {@code RestClient} do Spring, config-key {@code cnpjws-api}).
 */
@Component
public class CnpjWsClient {

  private final RestClient restClient;

  public CnpjWsClient(
      @Value("${app.integration.cnpjws.base-url:https://publica.cnpj.ws}") String baseUrl,
      @Value("${app.integration.cnpjws.connect-timeout-ms:3000}") long connectTimeoutMs,
      @Value("${app.integration.cnpjws.read-timeout-ms:5000}") long readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  public CnpjWsResponse buscar(String cnpj) {
    return restClient.get().uri("/cnpj/{cnpj}", cnpj).retrieve().body(CnpjWsResponse.class);
  }

  public static class CnpjWsResponse {
    public String cnpj;
    public String razao_social;
    public String nome_fantasia;
    public String data_inicio_atividade;
    public String porte;
    public NaturezaJuridica natureza_juridica;
    public Estabelecimento estabelecimento;
    public List<Cnae> cnaes_secundarios;

    public static class NaturezaJuridica {
      public String id;
      public String descricao;
    }

    public static class Estabelecimento {
      public String situacao_cadastral;
      public String email;
      public String telefone1;
      public String logradouro;
      public String numero;
      public String complemento;
      public String bairro;
      public String cep;
      public Municipio municipio;
      public Estado estado;
      public Cnae atividade_principal;
      public List<Cnae> atividades_secundarias;

      public static class Municipio {
        public String nome;
      }

      public static class Estado {
        public String sigla;
      }
    }

    public static class Cnae {
      public String codigo;
      public String descricao;
    }
  }
}
