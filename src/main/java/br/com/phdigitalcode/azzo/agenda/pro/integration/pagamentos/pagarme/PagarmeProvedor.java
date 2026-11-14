package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pagarme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;

/**
 * Stone / Pagar.me na conta do proprio salao, pela API core v5 da Pagar.me (a mesma que o Stone
 * Connect usa por baixo):
 *
 * <ul>
 *   <li><b>Pix</b>: {@code POST /orders} com {@code payment_method=pix}. O QR vem em
 *       {@code charges[].last_transaction.qr_code}. A Pagar.me exige cliente com documento.
 *   <li><b>Link</b>: {@code POST /paymentlinks} (cartao e Pix), um pagamento por link. Pago quando
 *       {@code total_paid_sessions > 0}.
 *   <li><b>Maquininha Stone (Stone Connect)</b>: {@code POST /orders} aberto ({@code closed=false})
 *       com {@code poi_payment_settings} e o serial da maquininha; exige o cabecalho
 *       {@code ServiceRefererName} (id de parceiro Stone). Cada passada vira um charge; depois de
 *       pago, o pedido TEM de ser fechado ({@code PATCH /orders/{id}/closed}), senao a maquininha
 *       para de receber pedidos novos (limite de 30 abertos). O Stone Connect nao tem sandbox nem
 *       API para listar maquininhas: o serial e digitado.
 * </ul>
 *
 * <p>Credenciais: a secret key ({@code sk_test_...} no teste, {@code sk_...} em producao; o
 * endereco e o mesmo) e, so para a maquininha, o id de parceiro Stone. O aviso nao e assinado pela
 * Pagar.me: vale o token da URL, e o status do aviso e sempre reconsultado na API antes de aplicar.
 */
@Component
public class PagarmeProvedor implements ProvedorDePagamento {

  static final String URL = "https://api.pagar.me/core/v5";

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public PagarmeProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return "PAGARME";
  }

  @Override
  public String nome() {
    return "Stone / Pagar.me";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.LINK, Capacidade.MAQUININHA_NUVEM, Capacidade.ESTORNO,
        Capacidade.ESTORNO_PARCIAL);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("secretKey", "Chave secreta (Desenvolvimento → Chaves, no painel da Pagar.me)", true),
        new CampoDeCredencial("serviceRefererName",
            "Id de parceiro Stone (só para a maquininha Stone pelo Stone Connect)", false, false));
  }

  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      cliente(conta).get().uri(u -> u.path("/orders").queryParam("size", 1).build()).retrieve().toBodilessEntity();
      String chave = conta.credencial("secretKey");
      boolean teste = chave != null && chave.startsWith("sk_test_");
      return new ResultadoValidacao(true, "Conta Pagar.me conectada" + (teste ? " (chave de teste)." : "."));
    } catch (RestClientResponseException e) {
      return new ResultadoValidacao(false, e.getStatusCode().value() == 401
          ? "A Pagar.me recusou a chave secreta. Confira se copiou a chave inteira (começa com sk_)."
          : "A Pagar.me respondeu " + e.getStatusCode().value() + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com a Pagar.me agora. Tente de novo em instantes.");
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    try {
      return switch (pedido.meio()) {
        case "PIX" -> {
          JsonNode pedidoCriado = postar(conta, "/orders", pedido.cobrancaId(), pedidoPix(pedido), false);
          JsonNode transacao = primeiroCharge(pedidoCriado).path("last_transaction");
          if ("failed".equals(texto(pedidoCriado, "status"))) {
            throw new FalhaNaOperadora("A Pagar.me recusou o Pix: "
                + primeiroNaoVazio(texto(transacao, "gateway_response_message"), "confira o cadastro do cliente (nome, e-mail e CPF)."), null);
          }
          yield new CobrancaCriada(texto(pedidoCriado, "id"), Status.AGUARDANDO, texto(transacao, "qr_code"), null,
              pedido.expiraEm());
        }
        case "LINK" -> {
          JsonNode link = postar(conta, "/paymentlinks", pedido.cobrancaId(), link(pedido), false);
          yield new CobrancaCriada(texto(link, "id"), Status.AGUARDANDO, null, texto(link, "url"), pedido.expiraEm());
        }
        case "CREDITO", "DEBITO" -> {
          if (pedido.terminalSerial() == null || pedido.terminalSerial().isBlank()) {
            throw new NaoSuportado(codigo(), "cartão sem maquininha");
          }
          if (blank(conta.credencial("serviceRefererName"))) {
            throw new FalhaNaOperadora("Para cobrar na maquininha Stone, informe o id de parceiro Stone na conta da "
                + "Pagar.me (Configurações → Integrações → Pagamentos).", null);
          }
          JsonNode criado = postar(conta, "/orders", pedido.cobrancaId(), pedidoMaquininha(pedido), true);
          yield new CobrancaCriada(texto(criado, "id"), Status.AGUARDANDO, null, null, pedido.expiraEm());
        }
        default -> throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
      };
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "criar a cobrança"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com a Pagar.me agora. Tente de novo em instantes.", e);
    }
  }

  /**
   * Consulta o pedido ({@code or_}) ou o link ({@code pl_}). Pedido da maquininha pago e ainda
   * aberto e fechado aqui — e a regra do Stone Connect.
   */
  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    try {
      if (idNaOperadora.startsWith("pl_")) {
        return statusDoLink(cliente(conta).get().uri("/paymentlinks/{id}", idNaOperadora).retrieve().body(JsonNode.class));
      }
      JsonNode pedido = obterPedido(conta, idNaOperadora);
      StatusNaOperadora status = statusDoPedido(pedido);
      if (status.status() == Status.PAGA && !pedido.path("closed").asBoolean(true)) fecharMelhorEsforco(conta, idNaOperadora, "paid");
      return status;
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança na Pagar.me agora.", e);
    }
  }

  @Override
  public void cancelar(Conta conta, String idNaOperadora) {
    try {
      if (idNaOperadora.startsWith("pl_")) {
        cliente(conta).patch().uri("/paymentlinks/{id}/cancel", idNaOperadora).retrieve().toBodilessEntity();
        return;
      }
      JsonNode pedido = obterPedido(conta, idNaOperadora);
      if (!pedido.path("closed").asBoolean(false)) {
        // Pedido aberto (maquininha): fechar como cancelado tira ele da maquininha.
        cliente(conta).patch().uri("/orders/{id}/closed", idNaOperadora).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("status", "canceled")).retrieve().toBodilessEntity();
        return;
      }
      // Pix ainda nao pago: cancelar o charge invalida o QR.
      for (JsonNode charge : pedido.path("charges")) {
        if ("pending".equals(texto(charge, "status"))) {
          cliente(conta).delete().uri("/charges/{id}", texto(charge, "id")).retrieve().toBodilessEntity();
        }
      }
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "cancelar a cobrança"), e);
    }
  }

  /** Estorna o charge pago (total, ou parcial com valor). No link, acha o pedido pelo codigo. */
  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    try {
      JsonNode pedido = idNaOperadora.startsWith("pl_") ? pedidoDoLink(conta, idNaOperadora) : obterPedido(conta, idNaOperadora);
      if (pedido == null) {
        throw new FalhaNaOperadora("Não achei na Pagar.me o pagamento deste link. Estorne pelo painel da Pagar.me.", null);
      }
      JsonNode pago = null;
      for (JsonNode charge : pedido.path("charges")) {
        if ("paid".equals(texto(charge, "status"))) pago = charge;
      }
      if (pago == null) throw new FalhaNaOperadora("A Pagar.me não tem pagamento confirmado para estornar nesta cobrança.", null);
      if (valor != null) {
        // DELETE com corpo: o RestClient nao tem body() no delete; vai pelo metodo generico.
        cliente(conta).method(org.springframework.http.HttpMethod.DELETE).uri("/charges/{id}", texto(pago, "id"))
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("amount", centavos(valor))).retrieve().toBodilessEntity();
      } else {
        cliente(conta).delete().uri("/charges/{id}", texto(pago, "id")).retrieve().toBodilessEntity();
      }
      return new StatusNaOperadora(valor == null ? Status.ESTORNADA : Status.ESTORNO_PARCIAL, null, null, null, null,
          texto(pago, "code"), null, null, null, valor == null ? reais(pago, "paid_amount") : valor, null);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "estornar"), e);
    }
  }

  /**
   * Aviso: {@code {id: "hook_...", type: "charge.paid", data: {...}}}. Nos eventos de charge o
   * pedido vem em {@code data.order.id}; nos de pedido, em {@code data.id}. Como a Pagar.me nao
   * assina o aviso, o status NAO vem dele: devolvemos o pedido e o servico reconsulta.
   */
  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    JsonNode raiz;
    try {
      raiz = json.readTree(corpo);
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso da Pagar.me ilegível.");
    }
    String tipo = texto(raiz, "type");
    JsonNode dados = raiz.path("data");
    String idDoPedido = tipo != null && tipo.startsWith("charge.") ? texto(dados.path("order"), "id")
        : tipo != null && tipo.startsWith("order.") ? texto(dados, "id") : null;
    String idDoEvento = primeiroNaoVazio(texto(raiz, "id"), tipo + ":" + idDoPedido + ":" + texto(dados, "status"));
    if (idDoPedido == null) return new AvisoDaOperadora(idDoEvento, tipo, null, null);
    return new AvisoDaOperadora(idDoEvento, tipo, idDoPedido, consultar(conta, idDoPedido));
  }

  // ---- corpos --------------------------------------------------------------------------------

  static Map<String, Object> pedidoPix(PedidoDeCobranca pedido) {
    Pagador p = pedido.pagador();
    if (p == null || blank(p.documento())) {
      throw new FalhaNaOperadora("O Pix pela Pagar.me exige cliente com CPF ou CNPJ no cadastro.", null);
    }
    Map<String, Object> pix = new LinkedHashMap<>();
    pix.put("expires_in", segundos(pedido.expiraEm(), Duration.ofMinutes(30)));
    Map<String, Object> pagamento = new LinkedHashMap<>();
    pagamento.put("payment_method", "pix");
    pagamento.put("pix", pix);
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("code", pedido.cobrancaId());
    corpo.put("items", List.of(item(pedido)));
    corpo.put("customer", cliente(pedido, true));
    corpo.put("payments", List.of(pagamento));
    return corpo;
  }

  static Map<String, Object> pedidoMaquininha(PedidoDeCobranca pedido) {
    Map<String, Object> setup = new LinkedHashMap<>();
    setup.put("type", "DEBITO".equals(pedido.meio()) ? "debit" : "credit");
    setup.put("installments", "DEBITO".equals(pedido.meio()) ? 1 : Math.max(1, pedido.parcelas()));
    setup.put("installment_type", "merchant");
    Map<String, Object> poi = new LinkedHashMap<>();
    poi.put("visible", true);
    poi.put("display_name", limitar(pedido.descricao() == null ? "Azzo" : pedido.descricao(), 30));
    poi.put("print_order_receipt", false);
    poi.put("devices_serial_number", List.of(pedido.terminalSerial()));
    poi.put("payment_setup", setup);
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("code", pedido.cobrancaId());
    corpo.put("closed", false);
    corpo.put("items", List.of(item(pedido)));
    corpo.put("customer", cliente(pedido, false));
    corpo.put("poi_payment_settings", poi);
    return corpo;
  }

  static Map<String, Object> link(PedidoDeCobranca pedido) {
    long total = centavos(pedido.valor());
    Map<String, Object> cartao = new LinkedHashMap<>();
    cartao.put("operation_type", "auth_and_capture");
    cartao.put("installments", List.of(Map.of("number", 1, "total", total)));
    Map<String, Object> pagamento = new LinkedHashMap<>();
    pagamento.put("accepted_payment_methods", List.of("credit_card", "pix"));
    pagamento.put("credit_card_settings", cartao);
    pagamento.put("pix_settings", Map.of("expires_in", 3600));
    Map<String, Object> carrinho = new LinkedHashMap<>();
    carrinho.put("items", List.of(Map.of(
        "amount", total,
        "name", limitar(pedido.descricao() == null ? "Pagamento" : pedido.descricao(), 64),
        "default_quantity", 1)));
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("type", "order");
    corpo.put("name", limitar(pedido.descricao() == null ? "Pagamento" : pedido.descricao(), 64));
    corpo.put("order_code", pedido.cobrancaId());
    corpo.put("max_paid_sessions", 1);
    corpo.put("is_building", false);
    long minutos = Math.max(1, segundos(pedido.expiraEm(), Duration.ofDays(1)) / 60);
    corpo.put("expires_in", minutos);
    corpo.put("payment_settings", pagamento);
    corpo.put("cart_settings", carrinho);
    return corpo;
  }

  private static Map<String, Object> item(PedidoDeCobranca pedido) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("amount", centavos(pedido.valor()));
    item.put("description", limitar(pedido.descricao() == null ? "Pagamento" : pedido.descricao(), 256));
    item.put("quantity", 1);
    item.put("code", "azzo");
    return item;
  }

  /** O Stone Connect pede so nome e e-mail; o Pix pede tambem documento e tipo. */
  private static Map<String, Object> cliente(PedidoDeCobranca pedido, boolean comDocumento) {
    Pagador p = pedido.pagador();
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("name", limitar(p == null || blank(p.nome()) ? "Cliente" : p.nome().trim(), 64));
    c.put("email", p == null || blank(p.email())
        ? "pagamento-" + pedido.cobrancaId().substring(0, 8) + "@clientes.azzoholding.com.br"
        : limitar(p.email().trim(), 64));
    if (comDocumento && p != null && !blank(p.documento())) {
      String doc = p.documento().replaceAll("\\D", "");
      c.put("document", doc);
      c.put("type", doc.length() > 11 ? "company" : "individual");
      String fone = p.telefone() == null ? "" : p.telefone().replaceAll("\\D", "");
      if (fone.startsWith("55") && fone.length() > 11) fone = fone.substring(2);
      if (fone.length() >= 10) {
        c.put("phones", Map.of("mobile_phone", Map.of(
            "country_code", "55", "area_code", fone.substring(0, 2), "number", fone.substring(2))));
      }
    }
    return c;
  }

  // ---- status --------------------------------------------------------------------------------

  /**
   * Status do pedido → status do Azzo. Na maquininha o pedido fica "pending" ate o Azzo fechar: o
   * que manda e o charge. Pago quando a soma dos charges pagos cobre o pedido.
   */
  static StatusNaOperadora statusDoPedido(JsonNode pedido) {
    String s = texto(pedido, "status");
    long valor = pedido.path("amount").asLong(0);
    long pago = 0;
    long estornado = 0;
    JsonNode ultimoPago = null;
    JsonNode ultimo = null;
    for (JsonNode charge : pedido.path("charges")) {
      ultimo = charge;
      String sc = texto(charge, "status");
      if ("paid".equals(sc)) {
        pago += charge.path("paid_amount").asLong(charge.path("amount").asLong(0));
        ultimoPago = charge;
      } else if ("refunded".equals(sc) || "canceled".equals(sc) && charge.has("paid_at")) {
        estornado += charge.path("amount").asLong(0);
        ultimoPago = charge;
      }
    }
    Status status;
    if (pago > 0 && pago >= valor) status = Status.PAGA;
    else if (estornado > 0 && pago == 0) status = Status.ESTORNADA;
    else if ("canceled".equals(s)) status = Status.CANCELADA;
    else if ("failed".equals(s)) status = Status.RECUSADA;
    else status = Status.AGUARDANDO;

    JsonNode meta = ultimoPago == null ? MissingNode.getInstance() : ultimoPago.path("metadata");
    String nsu = ultimoPago == null ? null : primeiroNaoVazio(texto(ultimoPago, "code"),
        texto(ultimoPago.path("last_transaction"), "acquirer_nsu"));
    String autorizacao = ultimoPago == null ? null : primeiroNaoVazio(texto(meta, "authorization_code"),
        texto(ultimoPago.path("last_transaction"), "acquirer_auth_code"));
    String bandeira = ultimoPago == null ? null : bandeira(
        primeiroNaoVazio(texto(meta, "scheme_name"), texto(ultimoPago.path("last_transaction").path("card"), "brand")),
        texto(meta, "account_funding_source"));
    String motivo = status == Status.RECUSADA && ultimo != null
        ? texto(ultimo.path("last_transaction"), "gateway_response_message") : null;
    BigDecimal valorPago = status == Status.PAGA ? BigDecimal.valueOf(pago, 2) : null;
    return new StatusNaOperadora(status, valorPago, null, null, null, nsu, autorizacao, bandeira, null,
        status == Status.ESTORNADA ? BigDecimal.valueOf(estornado, 2) : null, motivo);
  }

  static StatusNaOperadora statusDoLink(JsonNode link) {
    if (link.path("total_paid_sessions").asInt(0) > 0) return StatusNaOperadora.so(Status.PAGA);
    String s = texto(link, "status");
    if ("canceled".equals(s)) return StatusNaOperadora.so(Status.CANCELADA);
    if ("expired".equals(s) || "closed".equals(s)) return StatusNaOperadora.so(Status.EXPIRADA);
    return StatusNaOperadora.so(Status.AGUARDANDO);
  }

  private static String bandeira(String nome, String fonte) {
    if (nome == null) return null;
    String n = switch (nome.toLowerCase(Locale.ROOT)) {
      case "mastercard", "master" -> "Mastercard";
      case "visa" -> "Visa";
      case "elo" -> "Elo";
      case "amex", "american express" -> "American Express";
      case "hipercard" -> "Hipercard";
      default -> nome;
    };
    return "Debit".equalsIgnoreCase(fonte) ? n + " (débito)" : n;
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private JsonNode postar(Conta conta, String caminho, String idempotencia, Map<String, Object> corpo, boolean stoneConnect) {
    var req = cliente(conta).post().uri(caminho).header("Idempotency-Key", idempotencia);
    if (stoneConnect) req = req.header("ServiceRefererName", conta.credencial("serviceRefererName").trim());
    return req.contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(JsonNode.class);
  }

  private JsonNode obterPedido(Conta conta, String id) {
    return cliente(conta).get().uri("/orders/{id}", id).retrieve().body(JsonNode.class);
  }

  /** O pedido que o link gerou: a Pagar.me leva o {@code order_code} do link para o {@code code} do pedido. */
  private JsonNode pedidoDoLink(Conta conta, String idDoLink) {
    JsonNode link = cliente(conta).get().uri("/paymentlinks/{id}", idDoLink).retrieve().body(JsonNode.class);
    String codigo = texto(link, "order_code");
    if (codigo == null) return null;
    JsonNode lista = cliente(conta).get().uri(u -> u.path("/orders").queryParam("code", codigo).queryParam("size", 5).build())
        .retrieve().body(JsonNode.class);
    for (JsonNode p : lista.path("data")) {
      String id = texto(p, "id");
      if (id != null) return obterPedido(conta, id);
    }
    return null;
  }

  private void fecharMelhorEsforco(Conta conta, String id, String status) {
    try {
      cliente(conta).patch().uri("/orders/{id}/closed", id).contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("status", status)).retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      // A proxima consulta tenta de novo; o pagamento ja vale.
      org.slf4j.LoggerFactory.getLogger(PagarmeProvedor.class).warn("pagarme_fechar_pedido_falhou pedido={}", id, e);
    }
  }

  private static JsonNode primeiroCharge(JsonNode pedido) {
    JsonNode charges = pedido == null ? null : pedido.path("charges");
    return charges != null && charges.isArray() && charges.size() > 0 ? charges.get(0) : MissingNode.getInstance();
  }

  RestClient cliente(Conta conta) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    String basico = Base64.getEncoder().encodeToString(
        ((conta.credencial("secretKey") == null ? "" : conta.credencial("secretKey").trim()) + ":").getBytes(StandardCharsets.UTF_8));
    return RestClient.builder()
        .requestFactory(fabrica)
        .baseUrl(URL)
        .defaultHeader("Authorization", "Basic " + basico)
        .defaultHeader("User-Agent", "AzzoAgendaPro/1.0")
        .build();
  }

  private static String mensagem(RestClientResponseException e, String acao) {
    try {
      JsonNode corpo = new ObjectMapper().readTree(e.getResponseBodyAsString());
      String detalhe = texto(corpo, "message");
      JsonNode erros = corpo.path("errors");
      if (erros.isObject() && erros.size() > 0) {
        var campo = erros.fields().next();
        String primeiro = campo.getValue().isArray() && campo.getValue().size() > 0 ? campo.getValue().get(0).asText() : null;
        if (primeiro != null) detalhe = (detalhe == null ? "" : detalhe + " ") + primeiro;
      }
      if (detalhe != null) return "Pagar.me: " + detalhe;
    } catch (Exception ignorado) {
      // cai na mensagem generica
    }
    return "A Pagar.me não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  static long centavos(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
  }

  private static BigDecimal reais(JsonNode no, String campo) {
    return no.has(campo) ? BigDecimal.valueOf(no.path(campo).asLong(), 2) : null;
  }

  private static long segundos(Instant expiraEm, Duration padrao) {
    Duration d = expiraEm == null ? padrao : Duration.between(Instant.now(), expiraEm);
    return Math.max(60, d.getSeconds());
  }

  static String texto(JsonNode no, String campo) {
    if (no == null) return null;
    JsonNode v = no.path(campo);
    return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String primeiroNaoVazio(String... valores) {
    for (String v : valores) if (v != null && !v.isBlank()) return v;
    return null;
  }

  private static String limitar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
