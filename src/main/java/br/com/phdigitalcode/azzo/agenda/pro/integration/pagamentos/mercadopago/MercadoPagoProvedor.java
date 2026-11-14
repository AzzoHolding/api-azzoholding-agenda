package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.mercadopago;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;

/**
 * Mercado Pago na conta do proprio salao, pela API de Orders (a API nova, que unifica Point e
 * pagamento online; a de Payment Intents esta sendo descontinuada):
 *
 * <ul>
 *   <li><b>Maquininha Point</b>: {@code POST /v1/orders} tipo {@code point} com o
 *       {@code terminal_id}; a Point Smart carrega a order sozinha, a pessoa passa o cartao e o
 *       resultado volta pelo aviso (ou pela consulta).
 *   <li><b>Pix</b>: order {@code online} com {@code pix}/{@code bank_transfer} — devolve o copia e
 *       cola. Nao exige CPF de quem paga (exige e-mail; sem e-mail, vai um endereco tecnico).
 *   <li><b>Link</b>: order do Checkout Pro ({@code processing_mode=manual}) — devolve o
 *       {@code checkout_url}.
 * </ul>
 *
 * <p>Credenciais: o Access Token da conta do salao (o de teste comeca com {@code APP_USR} tambem; o
 * ambiente vem da conta) e, opcional mas recomendada, a chave secreta das notificacoes — com ela o
 * aviso e conferido pela assinatura {@code x-signature} (HMAC-SHA256).
 */
@Component
public class MercadoPagoProvedor implements ProvedorDePagamento {

  static final String URL = "https://api.mercadopago.com";

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public MercadoPagoProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return "MERCADO_PAGO";
  }

  @Override
  public String nome() {
    return "Mercado Pago";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.LINK, Capacidade.MAQUININHA_NUVEM, Capacidade.ESTORNO);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("accessToken", "Access Token (Suas integrações → Credenciais, no Mercado Pago)", true),
        new CampoDeCredencial("webhookSecret", "Chave secreta das notificações (Webhooks → Configurar notificações) — recomendada", true, false));
  }

  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      JsonNode eu = cliente(conta).get().uri("/users/me").retrieve().body(JsonNode.class);
      String apelido = texto(eu, "nickname");
      return new ResultadoValidacao(true, "Conta Mercado Pago conectada" + (apelido == null ? "." : ": " + apelido + "."));
    } catch (RestClientResponseException e) {
      return new ResultadoValidacao(false, e.getStatusCode().value() == 401
          ? "O Mercado Pago recusou o Access Token. Confira se copiou o token inteiro."
          : "O Mercado Pago respondeu " + e.getStatusCode().value() + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com o Mercado Pago agora. Tente de novo em instantes.");
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    Map<String, Object> order = switch (pedido.meio()) {
      case "PIX" -> orderPix(pedido);
      case "LINK" -> orderLink(pedido);
      case "CREDITO", "DEBITO" -> {
        if (pedido.terminalSerial() == null || pedido.terminalSerial().isBlank()) {
          throw new NaoSuportado(codigo(), "cartão sem maquininha");
        }
        yield orderPoint(pedido);
      }
      default -> throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    };
    try {
      JsonNode resposta = cliente(conta).post().uri("/v1/orders")
          .header("X-Idempotency-Key", pedido.cobrancaId())
          .contentType(MediaType.APPLICATION_JSON).body(order).retrieve().body(JsonNode.class);
      JsonNode metodo = primeiroPagamento(resposta).path("payment_method");
      return new CobrancaCriada(
          texto(resposta, "id"),
          status(resposta).status(),
          texto(metodo, "qr_code"),
          primeiroNaoVazio(texto(resposta, "checkout_url"), texto(metodo, "ticket_url")),
          pedido.expiraEm());
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "criar a cobrança"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com o Mercado Pago agora. Tente de novo em instantes.", e);
    }
  }

  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    try {
      return status(cliente(conta).get().uri("/v1/orders/{id}", idNaOperadora).retrieve().body(JsonNode.class));
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança no Mercado Pago agora.", e);
    }
  }

  /** Cancela order criada ou ja na maquininha (nesse caso o cancelamento e assincrono: confirma pelo aviso). */
  @Override
  public void cancelar(Conta conta, String idNaOperadora) {
    try {
      cliente(conta).post().uri("/v1/orders/{id}/cancel", idNaOperadora)
          .header("X-Idempotency-Key", "cancelar-" + idNaOperadora)
          .header("x-allow-cancelable-status", "at_terminal")
          .contentType(MediaType.APPLICATION_JSON).body(Map.of()).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "cancelar a cobrança"), e);
    }
  }

  /** Reembolso total da order (a API de Orders e o caminho; a de Payments nao deve ser misturada). */
  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    if (valor != null) throw new NaoSuportado(codigo(), "estorno parcial");
    try {
      JsonNode resposta = cliente(conta).post().uri("/v1/orders/{id}/refund", idNaOperadora)
          .header("X-Idempotency-Key", "estornar-" + idNaOperadora)
          .retrieve().body(JsonNode.class);
      return status(resposta);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "estornar"), e);
    }
  }

  /**
   * Aviso de order: {@code ?data.id=ORD...&type=order}, corpo com {@code action} e {@code data}.
   * Com a chave secreta configurada, a assinatura e obrigatoria e conferida:
   * {@code HMAC-SHA256(chave, "id:<data.id>;request-id:<x-request-id>;ts:<ts>;")} contra o
   * {@code v1} do {@code x-signature} (o id alfanumerico entra em minusculas).
   */
  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    JsonNode raiz;
    try {
      raiz = json.readTree(corpo);
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso do Mercado Pago ilegível.");
    }
    JsonNode dados = raiz.path("data");
    String idDaOrder = primeiroNaoVazio(cabecalhos.get("query.data.id"), texto(dados, "id"));
    String segredo = conta.credencial("webhookSecret");
    if (segredo != null && !segredo.isBlank()) {
      conferirAssinatura(segredo, cabecalhos.get("x-signature"), cabecalhos.get("x-request-id"), idDaOrder);
    }
    if (!"order".equals(texto(raiz, "type")) && !"order".equals(cabecalhos.get("query.type"))) {
      // Outros temas (reclamacoes, vinculacao...) nao mudam cobranca: guardados e ignorados.
      return new AvisoDaOperadora(texto(raiz, "action") + ":" + idDaOrder, texto(raiz, "action"), null, null);
    }
    String versao = texto(dados, "version");
    String idDoEvento = texto(raiz, "action") + ":" + idDaOrder + ":" + (versao == null ? texto(dados, "status") : versao);
    return new AvisoDaOperadora(idDoEvento, texto(raiz, "action"), idDaOrder, status(dados));
  }

  @Override
  public List<Terminal> terminais(Conta conta) {
    try {
      JsonNode resposta = cliente(conta).get()
          .uri(u -> u.path("/terminals/v1/list").queryParam("limit", 50).queryParam("offset", 0).build())
          .retrieve().body(JsonNode.class);
      List<Terminal> terminais = new ArrayList<>();
      for (JsonNode t : resposta.path("data").path("terminals")) {
        String id = texto(t, "id");
        if (id == null) continue;
        int separador = id.indexOf("__");
        String modelo = separador > 0 ? id.substring(0, separador).replace('_', ' ') : null;
        String serial = separador > 0 ? id.substring(separador + 2) : id;
        terminais.add(new Terminal(id, modelo, primeiroNaoVazio(texto(t, "external_pos_id"), serial)));
      }
      return terminais;
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível listar as maquininhas no Mercado Pago agora.", e);
    }
  }

  /** A Point so recebe order pela API em modo PDV. Ja em PDV: a operadora ignora. */
  @Override
  public void prepararTerminal(Conta conta, String serial) {
    try {
      cliente(conta).patch().uri("/terminals/v1/setup").contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("terminals", List.of(Map.of("id", serial, "operating_mode", "PDV"))))
          .retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "colocar a maquininha em modo integrado (PDV)"), e);
    }
  }

  // ---- corpos das orders ---------------------------------------------------------------------

  static Map<String, Object> orderPoint(PedidoDeCobranca pedido) {
    Map<String, Object> metodo = new LinkedHashMap<>();
    metodo.put("default_type", "DEBITO".equals(pedido.meio()) ? "debit_card" : "credit_card");
    if ("CREDITO".equals(pedido.meio()) && pedido.parcelas() > 1) metodo.put("default_installments", pedido.parcelas());
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("point", Map.of("terminal_id", pedido.terminalSerial(), "print_on_terminal", "no_ticket"));
    config.put("payment_method", metodo);
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("type", "point");
    order.put("external_reference", pedido.cobrancaId());
    order.put("description", limitar(pedido.descricao(), 150));
    order.put("transactions", Map.of("payments", List.of(Map.of("amount", valor(pedido.valor())))));
    order.put("config", config);
    return order;
  }

  static Map<String, Object> orderPix(PedidoDeCobranca pedido) {
    Map<String, Object> pagamento = new LinkedHashMap<>();
    pagamento.put("amount", valor(pedido.valor()));
    pagamento.put("payment_method", Map.of("id", "pix", "type", "bank_transfer"));
    pagamento.put("expiration_time", duracao(pedido.expiraEm(), Duration.ofMinutes(30)));
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("type", "online");
    order.put("processing_mode", "automatic");
    order.put("total_amount", valor(pedido.valor()));
    order.put("external_reference", pedido.cobrancaId());
    order.put("description", limitar(pedido.descricao(), 150));
    order.put("transactions", Map.of("payments", List.of(pagamento)));
    order.put("payer", pagador(pedido));
    return order;
  }

  static Map<String, Object> orderLink(PedidoDeCobranca pedido) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("title", limitar(pedido.descricao() == null ? "Pagamento" : pedido.descricao(), 150));
    item.put("unit_price", valor(pedido.valor()));
    item.put("quantity", 1);
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("type", "online");
    order.put("processing_mode", "manual");
    order.put("total_amount", valor(pedido.valor()));
    order.put("external_reference", pedido.cobrancaId());
    order.put("description", limitar(pedido.descricao(), 150));
    order.put("items", List.of(item));
    order.put("expiration_time", duracao(pedido.expiraEm(), Duration.ofDays(1)));
    if (pedido.pagador() != null && pedido.pagador().email() != null) order.put("payer", pagador(pedido));
    return order;
  }

  /** O Mercado Pago pede e-mail de quem paga; sem e-mail no cadastro, vai um endereco tecnico. */
  private static Map<String, Object> pagador(PedidoDeCobranca pedido) {
    Map<String, Object> payer = new LinkedHashMap<>();
    Pagador p = pedido.pagador();
    String email = p == null || p.email() == null || p.email().isBlank()
        ? "pagamento-" + pedido.cobrancaId().substring(0, 8) + "@clientes.azzoholding.com.br"
        : p.email().trim();
    payer.put("email", email);
    if (p != null && p.nome() != null && !p.nome().isBlank()) {
      String[] partes = p.nome().trim().split("\\s+", 2);
      payer.put("first_name", partes[0]);
      if (partes.length > 1) payer.put("last_name", partes[1]);
    }
    if (p != null && p.documento() != null && !p.documento().isBlank()) {
      String doc = p.documento().replaceAll("\\D", "");
      payer.put("identification", Map.of("type", doc.length() > 11 ? "CNPJ" : "CPF", "number", doc));
    }
    return payer;
  }

  // ---- status --------------------------------------------------------------------------------

  /** Status da order (ou do {@code data} do aviso) → status do Azzo, com NSU, bandeira e parcelas. */
  static StatusNaOperadora status(JsonNode order) {
    String s = texto(order, "status");
    String detalhe = texto(order, "status_detail");
    Status status = switch (s == null ? "" : s) {
      case "processed" -> Status.PAGA;
      case "failed" -> Status.RECUSADA;
      case "canceled", "cancelled" -> Status.CANCELADA;
      case "expired" -> Status.EXPIRADA;
      case "refunded" -> Status.ESTORNADA;
      default -> Status.AGUARDANDO; // created, at_terminal, processing, action_required
    };
    JsonNode pagamento = primeiroPagamento(order);
    JsonNode metodo = pagamento.path("payment_method");
    BigDecimal pago = decimal(order, "total_paid_amount");
    if (pago == null) pago = decimal(pagamento, "paid_amount");
    String motivo = status == Status.RECUSADA || status == Status.CANCELADA
        ? primeiroNaoVazio(texto(pagamento, "status_detail"), detalhe) : null;
    return new StatusNaOperadora(status, status == Status.PAGA ? pago : null, null, null, null,
        texto(pagamento.path("reference"), "id"), null, bandeira(texto(metodo, "id"), texto(metodo, "type")), null,
        status == Status.ESTORNADA ? pago : null, motivo);
  }

  private static String bandeira(String id, String tipo) {
    if (id == null || "pix".equals(id)) return null;
    String nome = switch (id) {
      case "master", "debmaster" -> "Mastercard";
      case "visa", "debvisa" -> "Visa";
      case "elo", "debelo" -> "Elo";
      case "amex" -> "American Express";
      case "hipercard" -> "Hipercard";
      default -> id.toUpperCase(Locale.ROOT);
    };
    return "debit_card".equals(tipo) ? nome + " (débito)" : nome;
  }

  private static JsonNode primeiroPagamento(JsonNode order) {
    JsonNode pagamentos = order == null ? null : order.path("transactions").path("payments");
    return pagamentos != null && pagamentos.isArray() && pagamentos.size() > 0 ? pagamentos.get(0) : json0();
  }

  private static JsonNode json0() {
    return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
  }

  // ---- assinatura ----------------------------------------------------------------------------

  static void conferirAssinatura(String segredo, String xSignature, String xRequestId, String dataId) {
    if (xSignature == null || xSignature.isBlank()) throw new AvisoInvalido("Aviso do Mercado Pago sem assinatura.");
    String ts = null;
    String v1 = null;
    for (String parte : xSignature.split(",")) {
      String[] kv = parte.split("=", 2);
      if (kv.length != 2) continue;
      if ("ts".equals(kv[0].trim())) ts = kv[1].trim();
      if ("v1".equals(kv[0].trim())) v1 = kv[1].trim();
    }
    if (ts == null || v1 == null) throw new AvisoInvalido("Assinatura do Mercado Pago incompleta.");
    StringBuilder manifesto = new StringBuilder();
    if (dataId != null && !dataId.isBlank()) manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
    if (xRequestId != null && !xRequestId.isBlank()) manifesto.append("request-id:").append(xRequestId).append(';');
    manifesto.append("ts:").append(ts).append(';');
    String calculado = hmac(segredo, manifesto.toString());
    if (!MessageDigest.isEqual(calculado.getBytes(StandardCharsets.US_ASCII), v1.getBytes(StandardCharsets.US_ASCII))) {
      throw new AvisoInvalido("Assinatura do aviso do Mercado Pago não confere.");
    }
  }

  static String hmac(String segredo, String texto) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ---- apoio ---------------------------------------------------------------------------------

  RestClient cliente(Conta conta) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder()
        .requestFactory(fabrica)
        .baseUrl(URL)
        .defaultHeader("Authorization", "Bearer " + conta.credencial("accessToken"))
        .defaultHeader("User-Agent", "AzzoAgendaPro/1.0")
        .build();
  }

  private static String mensagem(RestClientResponseException e, String acao) {
    try {
      JsonNode corpo = new ObjectMapper().readTree(e.getResponseBodyAsString());
      JsonNode erro = corpo.path("errors").path(0);
      String detalhe = primeiroNaoVazio(texto(erro, "message"), texto(corpo, "message"));
      if (detalhe != null) return "Mercado Pago: " + detalhe;
    } catch (Exception ignorado) {
      // cai na mensagem generica
    }
    return "O Mercado Pago não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  private static String valor(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  /** Duracao ISO 8601 ate o vencimento (minimo 1 minuto); sem vencimento, o padrao. */
  private static String duracao(Instant expiraEm, Duration padrao) {
    Duration d = expiraEm == null ? padrao : Duration.between(Instant.now(), expiraEm);
    if (d.isNegative() || d.toMinutes() < 1) d = Duration.ofMinutes(1);
    return d.withNanos(0).toString();
  }

  static String texto(JsonNode no, String campo) {
    if (no == null) return null;
    JsonNode v = no.path(campo);
    return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
  }

  private static BigDecimal decimal(JsonNode no, String campo) {
    String t = texto(no, campo);
    try {
      return t == null ? null : new BigDecimal(t);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String primeiroNaoVazio(String... valores) {
    for (String v : valores) if (v != null && !v.isBlank()) return v;
    return null;
  }

  private static String limitar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
