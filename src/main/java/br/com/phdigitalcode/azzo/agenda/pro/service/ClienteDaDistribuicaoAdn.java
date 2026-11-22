package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Distribuicao de documentos do Ambiente de Dados Nacional (ADN) para o contribuinte.
 *
 * <p>Contrato (manual "Guia de Utilizacao das APIs do ADN" v1.0, 12/02/2026, e implementacoes
 * publicas que batem entre si; o Swagger oficial so abre com certificado):
 *
 * <ul>
 *   <li>{@code GET {base}/DFe/{NSU}?lote=true&cnpjConsulta={cnpj}} com mTLS do certificado A1 —
 *       desde 03/11/2026 o {@code cnpjConsulta} e obrigatorio (sem ele: 400 REJEICAO). O CNPJ pode
 *       ser outro da mesma raiz do certificado.
 *   <li>Devolve ate 50 documentos com NSU MAIOR que o informado; o proximo pedido usa o maior NSU
 *       recebido.
 *   <li>Corpo: {@code StatusProcessamento} (DOCUMENTOS_LOCALIZADOS | NENHUM_DOCUMENTO_LOCALIZADO |
 *       REJEICAO), {@code LoteDFe[]} com {@code NSU}, {@code ChaveAcesso}, {@code TipoDocumento}
 *       (NFSE | EVENTO), {@code TipoEvento}, {@code ArquivoXml} (gzip + base64), e {@code Erros[]}.
 *   <li>404 com corpo = fim da fila; 400 com corpo = rejeicao; 429 = calma.
 * </ul>
 */
@Component
public class ClienteDaDistribuicaoAdn {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final FiscalCertificateService fiscalCertificateService;
  private final String baseHomologacao;
  private final String baseProducao;

  public ClienteDaDistribuicaoAdn(
      FiscalCertificateService fiscalCertificateService,
      @Value("${app.nfse.adn.base-url.homologacao:https://adn.producaorestrita.nfse.gov.br/contribuintes}")
          String baseHomologacao,
      @Value("${app.nfse.adn.base-url.producao:https://adn.nfse.gov.br/contribuintes}") String baseProducao) {
    this.fiscalCertificateService = fiscalCertificateService;
    this.baseHomologacao = baseHomologacao;
    this.baseProducao = baseProducao;
  }

  public record Documento(long nsu, String chave, String tipoDocumento, String tipoEvento, String xml) {}

  /** {@code fim}: nada depois deste NSU. {@code rejeicao}: o motivo, quando o ADN recusou. */
  public record Lote(int httpStatus, boolean fim, String rejeicao, List<Documento> documentos) {}

  /** O cliente com o certificado do salao. Senha errada ou certificado vencido sai daqui. */
  public RestClient cliente(String senhaDoCertificado) {
    try {
      FiscalCertificateService.KeyMaterial chave = fiscalCertificateService.loadActiveKeyMaterial(senhaDoCertificado);
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(null, null);
      char[] senha = senhaDoCertificado.toCharArray();
      java.security.cert.Certificate[] cadeia = chave.cadeiaParaTls();
      keyStore.setKeyEntry("nfse-adn", chave.privateKey(), senha, cadeia);
      org.slf4j.LoggerFactory.getLogger(ClienteDaDistribuicaoAdn.class)
          .info("nfse_adn.cliente cadeia_enviada={}", cadeia.length);
      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, senha);
      SSLContext ssl = SSLContext.getInstance("TLS");
      ssl.init(kmf.getKeyManagers(), null, null);
      // HTTP/2 com volta automatica para 1.1 (ALPN). O ADN de PRODUCAO fechava a conexao sem
      // responder ao HTTP/1.1 fixo ("header parser received no bytes", 07/11/2026), enquanto o de
      // homologacao aceitava. (A Sefin e outro servidor: la o 1.1 fixo continua.)
      HttpClient http = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .connectTimeout(Duration.ofSeconds(30))
          .sslContext(ssl)
          .build();
      JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
      fabrica.setReadTimeout(Duration.ofSeconds(60));
      return RestClient.builder().requestFactory(fabrica).build();
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalArgumentException("NFSE_CERTIFICATE_PASSWORD_INVALID", ex);
    }
  }

  public String baseDoAmbiente(String ambiente) {
    return "PRODUCAO".equals(ambiente) ? baseProducao : baseHomologacao;
  }

  public Lote buscar(RestClient cliente, String ambiente, long depoisDoNsu, String cnpj) {
    String url = baseDoAmbiente(ambiente) + "/DFe/" + Math.max(0, depoisDoNsu)
        + "?lote=true&cnpjConsulta=" + URLEncoder.encode(cnpj, StandardCharsets.UTF_8);
    try {
      return pedir(cliente, url);
    } catch (org.springframework.web.client.ResourceAccessException primeiraFalha) {
      // Conexao derrubada sem resposta: uma nova tentativa, numa conexao nova.
      return pedir(cliente, url);
    }
  }

  private Lote pedir(RestClient cliente, String url) {
    return cliente.get()
        .uri(URI.create(url))
        .header(HttpHeaders.ACCEPT, "application/json")
        // Sem identificacao, pedido de cliente Java pode ser descartado por firewall.
        .header(HttpHeaders.USER_AGENT, "AzzoAgendaPro-NFSe/1.0 (+https://app.azzoholding.com.br)")
        .exchange((pedido, resposta) -> {
          byte[] corpo = resposta.getBody().readAllBytes();
          return lerLote(resposta.getStatusCode().value(), new String(corpo, StandardCharsets.UTF_8));
        });
  }

  /** Separado da rede para ser testado com a resposta como texto. */
  static Lote lerLote(int status, String corpo) {
    JsonNode json = null;
    try {
      json = corpo == null || corpo.isBlank() ? null : JSON.readTree(corpo);
    } catch (Exception ignorado) {
      // corpo que nao e JSON: tratado abaixo pelo status
    }
    String situacao = json == null ? "" : texto(json, "StatusProcessamento").toUpperCase();
    if ("REJEICAO".equals(situacao)) {
      String motivo = erros(json);
      return new Lote(status, false, motivo.isBlank() ? "A Receita recusou a consulta (HTTP " + status + ")." : motivo, List.of());
    }
    if (status == 404 || "NENHUM_DOCUMENTO_LOCALIZADO".equals(situacao)) {
      return new Lote(status, true, null, List.of());
    }
    if (status == 429) {
      return new Lote(status, false, "LIMITE", List.of());
    }
    if (status < 200 || status >= 300 || json == null) {
      String motivo = json == null ? "" : erros(json);
      return new Lote(status, false, "A Receita respondeu HTTP " + status + (motivo.isBlank() ? "." : ": " + motivo), List.of());
    }
    List<Documento> documentos = new ArrayList<>();
    JsonNode lote = campo(json, "LoteDFe");
    if (lote != null && lote.isArray()) {
      for (JsonNode item : lote) {
        String nsu = texto(item, "NSU");
        if (nsu.isBlank()) continue;
        documentos.add(new Documento(
            Long.parseLong(nsu.replaceAll("\\D", "")),
            vazioParaNulo(texto(item, "ChaveAcesso")),
            texto(item, "TipoDocumento").toUpperCase(),
            vazioParaNulo(texto(item, "TipoEvento")),
            descompactar(texto(item, "ArquivoXml"))));
      }
    }
    return new Lote(status, documentos.isEmpty(), null, documentos);
  }

  static String descompactar(String base64) {
    if (base64 == null || base64.isBlank()) return null;
    try {
      byte[] bytes = Base64.getDecoder().decode(base64.replace('-', '+').replace('_', '/').replaceAll("\\s", ""));
      if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
          return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (Exception ex) {
      return null;
    }
  }

  private static String erros(JsonNode json) {
    JsonNode lista = campo(json, "Erros");
    if (lista == null || !lista.isArray()) return "";
    List<String> partes = new ArrayList<>();
    for (JsonNode erro : lista) {
      if (erro.isTextual()) {
        partes.add(erro.asText());
        continue;
      }
      String codigo = texto(erro, "Codigo");
      String descricao = texto(erro, "Descricao");
      String complemento = texto(erro, "Complemento");
      String linha = String.join(" – ",
          List.of(codigo, descricao, complemento).stream().filter(s -> !s.isBlank()).toList());
      if (!linha.isBlank()) partes.add(linha);
    }
    return String.join(" | ", partes);
  }

  /** O ADN usa PascalCase; aceita tambem camelCase por garantia. */
  private static JsonNode campo(JsonNode json, String nome) {
    if (json == null) return null;
    JsonNode valor = json.get(nome);
    if (valor == null) valor = json.get(Character.toLowerCase(nome.charAt(0)) + nome.substring(1));
    return valor;
  }

  private static String texto(JsonNode json, String nome) {
    JsonNode valor = campo(json, nome);
    return valor == null || valor.isNull() ? "" : valor.asText("").trim();
  }

  private static String vazioParaNulo(String valor) {
    return valor == null || valor.isBlank() ? null : valor;
  }
}
