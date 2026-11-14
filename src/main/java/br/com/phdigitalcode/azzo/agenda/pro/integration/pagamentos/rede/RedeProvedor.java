package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.rede;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;

/**
 * Rede (Itau) na conta do proprio salao, pela API e.Rede v2 com OAuth 2.0: o PV e o
 * {@code clientId}, a Chave de Integracao e o {@code clientSecret}; o token dura 24 minutos e fica
 * em memoria por conta.
 *
 * <p>Nesta fase so o <b>Pix</b>: {@code POST /v2/transactions} com {@code kind=Pix} devolve o
 * copia e cola ({@code qrCodeResponse.qrCodeData}) e o {@code tid}; consulta por tid
 * ({@code Pending}, {@code Approved}, {@code Canceled}; o codigo 3036 e QR expirado); devolucao
 * total ou parcial em {@code /v2/transactions/{tid}/refunds}.
 *
 * <p>Aviso: a Rede manda {@code {events, data:{id: tid}}} e, se o estabelecimento cadastrou, o
 * cabecalho {@code authorization} com o valor combinado. Com o campo "Valor do cabeçalho
 * authorization" preenchido, aviso sem ele e recusado. O status vem sempre da consulta.
 */
@Component
public class RedeProvedor implements ProvedorDePagamento {

  private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final Map<String, Token> tokens = new ConcurrentHashMap<>();

  private record Token(String valor, Instant expiraEm) {}

  public RedeProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return "REDE";
  }

  @Override
  public String nome() {
    return "Rede";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.ESTORNO, Capacidade.ESTORNO_PARCIAL);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("pv", "PV (número de filiação, no portal Use Rede)", false),
        new CampoDeCredencial("chaveDeIntegracao", "Chave de Integração (portal Use Rede → e.Rede)", true),
        new CampoDeCredencial("segredoDoAviso",
            "Valor do cabeçalho authorization do aviso (o mesmo cadastrado na Rede) — recomendado", true, false));
  }

  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      tokens.remove(conta.id());
      token(conta);
      return new ResultadoValidacao(true, "Conta Rede conectada.");
    } catch (RestClientResponseException e) {
      return new ResultadoValidacao(false, e.getStatusCode().value() == 401 || e.getStatusCode().value() == 400
          ? "A Rede recusou o PV/Chave de Integração. Confira o ambiente (teste ou produção) e as duas credenciais."
          : "A Rede respondeu " + e.getStatusCode().value() + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com a Rede agora. Tente de novo em instantes.");
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"PIX".equals(pedido.meio())) throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    try {
      JsonNode r = api(conta).post().uri("/v2/transactions").contentType(MediaType.APPLICATION_JSON)
          .body(transacaoPix(pedido)).retrieve().body(JsonNode.class);
      String copiaECola = texto(r.path("qrCodeResponse"), "qrCodeData");
      if (copiaECola == null) {
        throw new FalhaNaOperadora("A Rede não gerou o Pix: "
            + primeiroNaoVazio(texto(r, "returnMessage"), "confira se o Pix está habilitado no seu PV."), null);
      }
      return new CobrancaCriada(texto(r, "tid"), Status.AGUARDANDO, copiaECola, null, pedido.expiraEm());
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "criar o Pix"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com a Rede agora. Tente de novo em instantes.", e);
    }
  }

  @Override
  public StatusNaOperadora consultar(Conta conta, String tid) {
    try {
      return status(api(conta).get().uri("/v2/transactions/{tid}", tid).retrieve().body(JsonNode.class));
    } catch (RestClientResponseException e) {
      // QR expirado volta como erro com o codigo 3036.
      if (e.getResponseBodyAsString().contains("3036")) return StatusNaOperadora.so(Status.EXPIRADA);
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança na Rede agora.", e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança na Rede agora.", e);
    }
  }

  /** Pix nao pago na Rede nao tem cancelamento: ele expira. So o pago e devolvido. */
  @Override
  public void cancelar(Conta conta, String tid) {
    if (consultar(conta, tid).status() == Status.PAGA) {
      throw new FalhaNaOperadora("O Pix já foi pago na Rede: use o estorno.", null);
    }
  }

  @Override
  public StatusNaOperadora estornar(Conta conta, String tid, BigDecimal valor) {
    try {
      long centavos;
      if (valor != null) {
        centavos = centavos(valor);
      } else {
        JsonNode t = api(conta).get().uri("/v2/transactions/{tid}", tid).retrieve().body(JsonNode.class);
        centavos = primeiroNumero(t, "amount");
      }
      JsonNode r = api(conta).post().uri("/v2/transactions/{tid}/refunds", tid).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("amount", centavos)).retrieve().body(JsonNode.class);
      String codigo = texto(r, "returnCode");
      if (codigo != null && !codigo.equals("359") && !codigo.equals("360") && !codigo.equals("00")) {
        throw new FalhaNaOperadora("A Rede não confirmou a devolução: " + primeiroNaoVazio(texto(r, "returnMessage"), codigo), null);
      }
      return new StatusNaOperadora(valor == null ? Status.ESTORNADA : Status.ESTORNO_PARCIAL, null, null, null, null,
          texto(r, "nsu"), null, null, null, BigDecimal.valueOf(centavos, 2), null);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "devolver o Pix"), e);
    }
  }

  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    String segredo = conta.credencial("segredoDoAviso");
    if (segredo != null && !segredo.isBlank()) {
      String recebido = cabecalhos.get("authorization");
      if (recebido == null || !MessageDigest.isEqual(segredo.trim().getBytes(StandardCharsets.UTF_8),
          recebido.trim().getBytes(StandardCharsets.UTF_8))) {
        throw new AvisoInvalido("Aviso da Rede sem o cabeçalho combinado.");
      }
    }
    JsonNode raiz;
    try {
      raiz = json.readTree(corpo);
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso da Rede ilegível.");
    }
    String tid = texto(raiz.path("data"), "id");
    String evento = raiz.path("events").path(0).asText("aviso");
    String idDoAviso = primeiroNaoVazio(texto(raiz, "id"), cabecalhos.get("request-id"));
    if (tid == null) return new AvisoDaOperadora(evento + ":" + idDoAviso, evento, null, null);
    StatusNaOperadora status = consultar(conta, tid);
    return new AvisoDaOperadora(tid + ":" + evento + ":" + status.status(), evento, tid, status);
  }

  // ---- corpos e status -----------------------------------------------------------------------

  static Map<String, Object> transacaoPix(PedidoDeCobranca pedido) {
    Instant expira = pedido.expiraEm() == null ? Instant.now().plus(Duration.ofMinutes(30)) : pedido.expiraEm();
    Map<String, Object> qr = new LinkedHashMap<>();
    qr.put("dateTimeExpiration", LocalDateTime.ofInstant(expira, BRASILIA).withNano(0)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("kind", "Pix");
    // A Rede nao aceita caractere especial: o id da cobranca vai sem os hifens (32 caracteres).
    String referencia = pedido.cobrancaId().replace("-", "");
    t.put("reference", referencia);
    t.put("orderId", referencia);
    t.put("amount", centavos(pedido.valor()));
    t.put("qrCode", qr);
    return t;
  }

  /** {@code Pending} esperando; {@code Approved} pago; {@code Canceled} devolvido. */
  static StatusNaOperadora status(JsonNode t) {
    String s = primeiroNaoVazio(texto(t.path("authorization"), "status"), texto(t, "status"));
    if ("3036".equals(texto(t, "returnCode"))) return StatusNaOperadora.so(Status.EXPIRADA);
    Status status = switch (s == null ? "" : s) {
      case "Approved" -> Status.PAGA;
      case "Canceled" -> Status.ESTORNADA;
      case "Denied" -> Status.RECUSADA;
      default -> Status.AGUARDANDO;
    };
    long valor = primeiroNumero(t, "amount");
    if (valor == 0) valor = primeiroNumero(t.path("authorization"), "amount");
    String nsu = primeiroNaoVazio(texto(t.path("authorization"), "nsu"), texto(t, "nsu"));
    return new StatusNaOperadora(status, status == Status.PAGA && valor > 0 ? BigDecimal.valueOf(valor, 2) : null,
        null, null, null, nsu, null, null, null, null, null);
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private RestClient api(Conta conta) {
    return base(conta.producao() ? "https://api.userede.com.br/erede" : "https://sandbox-erede.useredecloud.com.br")
        .defaultHeader("Authorization", "Bearer " + token(conta)).build();
  }

  private String token(Conta conta) {
    Token atual = tokens.get(conta.id());
    if (atual != null && atual.expiraEm().isAfter(Instant.now().plusSeconds(60))) return atual.valor();
    String url = conta.producao() ? "https://api.userede.com.br/redelabs/oauth2/token"
        : "https://rl7-sandbox-api.useredecloud.com.br/oauth2/token";
    String basico = Base64.getEncoder().encodeToString(
        (valorOuVazio(conta.credencial("pv")) + ":" + valorOuVazio(conta.credencial("chaveDeIntegracao")))
            .getBytes(StandardCharsets.UTF_8));
    LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    JsonNode r = base(url).build().post().header("Authorization", "Basic " + basico)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
    String valor = texto(r, "access_token");
    if (valor == null) throw new FalhaNaOperadora("A Rede não devolveu o token de acesso.", null);
    long segundos = r.path("expires_in").asLong(1440);
    tokens.put(conta.id(), new Token(valor, Instant.now().plusSeconds(segundos)));
    return valor;
  }

  private RestClient.Builder base(String url) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder().requestFactory(fabrica).baseUrl(url).defaultHeader("User-Agent", "AzzoAgendaPro/1.0");
  }

  private static String mensagem(RestClientResponseException e, String acao) {
    try {
      JsonNode corpo = new ObjectMapper().readTree(e.getResponseBodyAsString());
      String detalhe = texto(corpo, "returnMessage");
      if (detalhe != null) return "Rede: " + detalhe;
    } catch (Exception ignorado) {
      // cai na mensagem generica
    }
    return "A Rede não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  static long centavos(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
  }

  private static long primeiroNumero(JsonNode no, String campo) {
    return no == null ? 0 : no.path(campo).asLong(0);
  }

  static String texto(JsonNode no, String campo) {
    if (no == null) return null;
    JsonNode v = no.path(campo);
    return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
  }

  private static String valorOuVazio(String s) {
    return s == null ? "" : s.trim();
  }

  private static String primeiroNaoVazio(String... valores) {
    for (String v : valores) if (v != null && !v.isBlank()) return v;
    return null;
  }
}
