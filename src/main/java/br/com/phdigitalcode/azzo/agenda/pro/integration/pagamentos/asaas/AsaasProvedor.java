package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.asaas;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
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

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;

/**
 * Asaas na conta do proprio salao: Pix e link de pagamento (fatura com Pix, boleto e cartao).
 *
 * <p>Diferente do {@code AsaasClient} da mensalidade do Azzo (uma URL global), aqui a URL segue o
 * AMBIENTE da conta do salao: sandbox ou producao. Com a URL global, uma chave de producao era
 * validada contra o sandbox (achado registrado em {@code ServicoTenantPayments}).
 *
 * <p>O Asaas exige cliente com CPF/CNPJ em toda cobranca; o cliente e procurado pelo documento na
 * conta do salao e criado se nao existir.
 */
@Component
public class AsaasProvedor implements ProvedorDePagamento {

  static final String URL_SANDBOX = "https://api-sandbox.asaas.com/v3";
  static final String URL_PRODUCAO = "https://api.asaas.com/v3";
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public AsaasProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return "ASAAS";
  }

  @Override
  public String nome() {
    return "Asaas";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.LINK, Capacidade.ESTORNO, Capacidade.ESTORNO_PARCIAL);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(new CampoDeCredencial("apiKey", "Chave de API (Integrações → Chave de API, no Asaas)", true));
  }

  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      cliente(conta).get().uri("/myAccount").retrieve().body(JsonNode.class);
      return new ResultadoValidacao(true, "Conta Asaas conectada.");
    } catch (RestClientResponseException e) {
      return new ResultadoValidacao(false, e.getStatusCode().value() == 401
          ? "O Asaas recusou a chave. Confira se é a chave de " + (conta.producao() ? "produção" : "sandbox") + "."
          : "O Asaas respondeu " + e.getStatusCode().value() + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com o Asaas agora. Tente de novo em instantes.");
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"PIX".equals(pedido.meio()) && !"LINK".equals(pedido.meio())) {
      throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    }
    try {
      String clienteId = clienteNaConta(conta, pedido.pagador());
      Map<String, Object> corpo = new LinkedHashMap<>();
      corpo.put("customer", clienteId);
      corpo.put("billingType", "PIX".equals(pedido.meio()) ? "PIX" : "UNDEFINED");
      corpo.put("value", pedido.valor());
      corpo.put("dueDate", vencimento(pedido.expiraEm()).toString());
      corpo.put("description", pedido.descricao());
      corpo.put("externalReference", pedido.cobrancaId());
      JsonNode cobranca = cliente(conta).post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
          .body(corpo).retrieve().body(JsonNode.class);
      String id = texto(cobranca, "id");
      String pix = null;
      Instant expira = pedido.expiraEm();
      if ("PIX".equals(pedido.meio())) {
        JsonNode qr = cliente(conta).get().uri("/payments/{id}/pixQrCode", id).retrieve().body(JsonNode.class);
        pix = texto(qr, "payload");
        Instant expiraPix = instante(texto(qr, "expirationDate"));
        if (expiraPix != null) expira = expiraPix;
      }
      return new CobrancaCriada(id, Status.AGUARDANDO, pix, texto(cobranca, "invoiceUrl"), expira);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagemDoAsaas(e, "criar a cobrança"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com o Asaas agora. Tente de novo em instantes.", e);
    }
  }

  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    try {
      return status(cliente(conta).get().uri("/payments/{id}", idNaOperadora).retrieve().body(JsonNode.class));
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança no Asaas agora.", e);
    }
  }

  @Override
  public void cancelar(Conta conta, String idNaOperadora) {
    try {
      cliente(conta).delete().uri("/payments/{id}", idNaOperadora).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagemDoAsaas(e, "cancelar a cobrança"), e);
    }
  }

  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    try {
      Map<String, Object> corpo = valor == null ? Map.of() : Map.of("value", valor);
      JsonNode resposta = cliente(conta).post().uri("/payments/{id}/refund", idNaOperadora)
          .contentType(MediaType.APPLICATION_JSON).body(corpo).retrieve().body(JsonNode.class);
      return status(resposta);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagemDoAsaas(e, "estornar"), e);
    }
  }

  /**
   * O Asaas manda {@code {"id": "evt_...", "event": "PAYMENT_RECEIVED", "payment": {...}}} e o
   * token configurado no cabecalho {@code asaas-access-token}. O token da URL ja identificou a
   * conta; se o cabecalho vier, tem de bater com o da conta.
   */
  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    String tokenNoCabecalho = cabecalhos.get("asaas-access-token");
    String tokenDaConta = conta.credencial("webhookToken");
    if (tokenNoCabecalho != null && tokenDaConta != null && !tokenDaConta.equals(tokenNoCabecalho)) {
      throw new AvisoInvalido("Token do aviso do Asaas não confere.");
    }
    try {
      JsonNode raiz = json.readTree(corpo);
      JsonNode pagamento = raiz.path("payment");
      String evento = texto(raiz, "event");
      String idDoEvento = texto(raiz, "id");
      if (idDoEvento == null) idDoEvento = evento + ":" + texto(pagamento, "id") + ":" + texto(pagamento, "status");
      return new AvisoDaOperadora(idDoEvento, evento, texto(pagamento, "id"), status(pagamento));
    } catch (AvisoInvalido e) {
      throw e;
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso do Asaas ilegível.");
    }
  }

  // ---- apoio -------------------------------------------------------------------------------

  RestClient cliente(Conta conta) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder()
        .requestFactory(fabrica)
        .baseUrl(conta.producao() ? URL_PRODUCAO : URL_SANDBOX)
        .defaultHeader("access_token", conta.credencial("apiKey"))
        .defaultHeader("User-Agent", "AzzoAgendaPro/1.0")
        .build();
  }

  private String clienteNaConta(Conta conta, Pagador pagador) {
    if (pagador == null || pagador.documento() == null || pagador.documento().isBlank()) {
      throw new FalhaNaOperadora("O Asaas exige o CPF ou CNPJ de quem paga. Preencha no cadastro do cliente.", null);
    }
    String documento = pagador.documento().replaceAll("\\D", "");
    JsonNode encontrados = cliente(conta).get().uri(u -> u.path("/customers").queryParam("cpfCnpj", documento).build())
        .retrieve().body(JsonNode.class);
    JsonNode primeiro = encontrados == null ? null : encontrados.path("data").path(0);
    if (primeiro != null && !primeiro.isMissingNode() && texto(primeiro, "id") != null) return texto(primeiro, "id");
    Map<String, Object> novo = new LinkedHashMap<>();
    novo.put("name", pagador.nome());
    novo.put("cpfCnpj", documento);
    if (pagador.email() != null) novo.put("email", pagador.email());
    if (pagador.telefone() != null) novo.put("mobilePhone", pagador.telefone());
    if (pagador.id() != null) novo.put("externalReference", "client:" + pagador.id());
    return texto(cliente(conta).post().uri("/customers").contentType(MediaType.APPLICATION_JSON).body(novo)
        .retrieve().body(JsonNode.class), "id");
  }

  /** Status do Asaas → status do Azzo. Taxa e liquido: {@code value - netValue}. */
  static StatusNaOperadora status(JsonNode pagamento) {
    String s = texto(pagamento, "status");
    Status status = switch (s == null ? "" : s) {
      case "RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH" -> Status.PAGA;
      case "OVERDUE" -> Status.EXPIRADA;
      case "REFUNDED" -> Status.ESTORNADA;
      case "REFUND_IN_PROGRESS", "REFUND_REQUESTED" -> Status.ESTORNO_PARCIAL;
      case "DELETED" -> Status.CANCELADA;
      default -> Status.AGUARDANDO;
    };
    if (pagamento.path("deleted").asBoolean(false)) status = Status.CANCELADA;
    BigDecimal valor = decimal(pagamento, "value");
    BigDecimal liquido = decimal(pagamento, "netValue");
    BigDecimal taxa = valor != null && liquido != null ? valor.subtract(liquido) : null;
    LocalDate previsao = data(texto(pagamento, "estimatedCreditDate"));
    return new StatusNaOperadora(status, status == Status.PAGA ? valor : null, taxa, liquido, previsao,
        texto(pagamento, "nossoNumero"), null, texto(pagamento.path("creditCard"), "creditCardBrand"),
        texto(pagamento.path("creditCard"), "creditCardNumber"), null, null);
  }

  private static LocalDate vencimento(Instant expiraEm) {
    LocalDate hoje = LocalDate.now(FUSO);
    if (expiraEm == null) return hoje.plusDays(1);
    LocalDate dia = LocalDate.ofInstant(expiraEm, FUSO);
    return dia.isBefore(hoje) ? hoje : dia;
  }

  private static String mensagemDoAsaas(RestClientResponseException e, String acao) {
    String corpo = e.getResponseBodyAsString();
    int i = corpo == null ? -1 : corpo.indexOf("\"description\"");
    if (i >= 0) {
      int a = corpo.indexOf('"', corpo.indexOf(':', i) + 1);
      int b = a < 0 ? -1 : corpo.indexOf('"', a + 1);
      if (a >= 0 && b > a) return "Asaas: " + corpo.substring(a + 1, b);
    }
    return "O Asaas não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  static String texto(JsonNode no, String campo) {
    if (no == null) return null;
    JsonNode v = no.path(campo);
    return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
  }

  private static BigDecimal decimal(JsonNode no, String campo) {
    JsonNode v = no == null ? null : no.path(campo);
    return v == null || v.isMissingNode() || v.isNull() ? null : v.decimalValue();
  }

  private static LocalDate data(String valor) {
    try {
      return valor == null ? null : LocalDate.parse(valor.substring(0, 10));
    } catch (Exception e) {
      return null;
    }
  }

  private static Instant instante(String valor) {
    try {
      return valor == null ? null : LocalDateTime.parse(valor.replace(' ', 'T')).atZone(FUSO).toInstant();
    } catch (Exception e) {
      return null;
    }
  }
}
