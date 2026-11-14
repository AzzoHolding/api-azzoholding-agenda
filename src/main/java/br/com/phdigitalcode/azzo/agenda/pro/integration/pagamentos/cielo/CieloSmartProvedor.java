package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.cielo;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
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
 * Maquininha Cielo Smart pela nuvem: API de Integracao Remota (Order Manager).
 *
 * <p>O Azzo cria o pedido ({@code POST /orders}, valores em centavos, {@code terminal_number} = o
 * numero logico da maquininha escolhida) e o libera para pagamento ({@code PUT
 * /orders/{id}?operation=PLACE}); a maquininha busca o pedido na nuvem e a cliente paga nela. O
 * status vem da consulta ({@code GET /orders/{id}} e {@code GET /orders/{id}/transactions}); o aviso
 * da Cielo (cadastrado pelo suporte dela) so dispara a reconsulta — o corpo nunca e confiado.
 *
 * <p>Conta propria, separada da Cielo E-commerce (Pix): sao outras credenciais (Client-ID, Access
 * Token e Merchant ID do portal de desenvolvedores, API "Cielo Smart - Order Manager").
 *
 * <p>O que a documentacao NAO oferece (14/11/2026), e como fica:
 * <ul>
 *   <li>cancelar pedido ja liberado: so pedido em DRAFT pode ser apagado. O Azzo tenta apagar e,
 *       se a Cielo recusar, tenta fechar ({@code operation=CLOSE}); se nada der, pede para cancelar
 *       na maquininha — a confirmar no teste real;</li>
 *   <li>estorno remoto: nao existe. O cancelamento e feito na maquininha; depois o Azzo ve a
 *       transacao CANCELLATION na consulta e o estorno da comanda passa.</li>
 * </ul>
 * Modelos com pedido para uma maquininha so: L400, L300 V4 e N950U (Order Service 2.3.1+). Nos
 * outros, o pedido aparece em todas as maquininhas do estabelecimento.
 */
@Component
public class CieloSmartProvedor implements ProvedorDePagamento {

  public static final String CODIGO = "CIELO_SMART";

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public CieloSmartProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return CODIGO;
  }

  @Override
  public String nome() {
    return "Cielo Smart (maquininha)";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.MAQUININHA_NUVEM, Capacidade.ESTORNO);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("clientId", "Client-ID (portal de desenvolvedores Cielo, API \"Cielo Smart - Order Manager\")", false),
        new CampoDeCredencial("accessToken", "Access Token", true),
        new CampoDeCredencial("merchantId", "Merchant ID (em produção, pedido ao suporte da Cielo: \"Token Integração Remota\")", false));
  }

  /** Lista um pedido so: 200 = credenciais aceitas; 401/403 = recusadas. */
  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      cliente(conta).get().uri(u -> u.path("/orders").queryParam("page", 0).queryParam("page_size", 1).build())
          .retrieve().toBodilessEntity();
      return new ResultadoValidacao(true, "Conta Cielo Smart conectada. Agora adicione a maquininha pelo número lógico.");
    } catch (RestClientResponseException e) {
      int codigo = e.getStatusCode().value();
      return new ResultadoValidacao(false, codigo == 401 || codigo == 403
          ? "A Cielo recusou o Client-ID, o Access Token ou o Merchant ID. Confira o ambiente (teste ou produção) e os três campos."
          : "A Cielo respondeu " + codigo + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com a Cielo agora. Tente de novo em instantes.");
    }
  }

  /** A Cielo nao lista as maquininhas pela API: o dono digita o numero logico. */
  @Override
  public List<Terminal> terminais(Conta conta) {
    return List.of();
  }

  @Override
  public void prepararTerminal(Conta conta, String serial) {
    if (serial == null || !serial.trim().matches("[0-9]{6,10}(-[0-9])?")) {
      throw new FalhaNaOperadora("Digite o número lógico da maquininha Cielo (só números, com ou sem o dígito: 1234567-8).", null);
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"CREDITO".equals(pedido.meio()) && !"DEBITO".equals(pedido.meio())) {
      throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    }
    RestClient cielo = cliente(conta);
    String id;
    try {
      JsonNode criado = cielo.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
          .body(pedidoDaComanda(pedido)).retrieve().body(JsonNode.class);
      id = CieloProvedor.texto(criado, "id");
      if (id == null) throw new FalhaNaOperadora("A Cielo não devolveu o número do pedido.", null);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "criar o pedido"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com a Cielo agora. Tente de novo em instantes.", e);
    }
    try {
      cielo.put().uri(u -> u.path("/orders/{id}").queryParam("operation", "PLACE").build(id)).retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      // Pedido em DRAFT pode ser apagado: nao deixa lixo na Cielo.
      apagarSemFalhar(cielo, id);
      throw new FalhaNaOperadora(e instanceof RestClientResponseException r ? mensagem(r, "mandar o pedido para a maquininha")
          : "Não foi possível mandar o pedido para a maquininha agora. Tente de novo.", e);
    }
    return new CobrancaCriada(id, Status.AGUARDANDO, null, null, pedido.expiraEm());
  }

  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    RestClient cielo = cliente(conta);
    try {
      JsonNode pedido = cielo.get().uri("/orders/{id}", idNaOperadora).retrieve().body(JsonNode.class);
      JsonNode transacoes;
      try {
        transacoes = cielo.get().uri("/orders/{id}/transactions", idNaOperadora).retrieve().body(JsonNode.class);
      } catch (RestClientResponseException e) {
        transacoes = null; // sem a lista, vale a que vem no proprio pedido
      }
      return status(pedido, transacoes);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar o pedido na Cielo agora.", e);
    }
  }

  @Override
  public void cancelar(Conta conta, String idNaOperadora) {
    StatusNaOperadora atual = consultar(conta, idNaOperadora);
    if (atual.status() == Status.PAGA) throw new FalhaNaOperadora("A cliente já pagou na maquininha: use o estorno.", null);
    if (atual.status() == Status.CANCELADA) return;
    RestClient cielo = cliente(conta);
    if (apagarSemFalhar(cielo, idNaOperadora)) return;
    try {
      cielo.put().uri(u -> u.path("/orders/{id}").queryParam("operation", "CLOSE").build(idNaOperadora))
          .retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("A Cielo não deixou tirar o pedido da maquininha. Cancele o pedido na própria "
          + "maquininha e tente de novo.", e);
    }
  }

  /**
   * Nao ha estorno pela API: o cancelamento e feito na maquininha. Se ja foi, a consulta mostra a
   * transacao de cancelamento e o estorno da comanda passa; se nao, explica o que fazer.
   */
  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    StatusNaOperadora atual = consultar(conta, idNaOperadora);
    if (atual.status() == Status.ESTORNADA || atual.status() == Status.ESTORNO_PARCIAL) return atual;
    throw new FalhaNaOperadora("Na Cielo Smart, o cancelamento da venda é feito na própria maquininha (com o cartão). "
        + "Depois que a maquininha confirmar, estorne a comanda de novo no Azzo.", null);
  }

  /**
   * O aviso da Cielo nao e assinado: o token da URL acha a conta, e o status vem sempre da consulta.
   * Aviso de mudanca de pedido traz {@code id}; de transacao feita, {@code order_id}.
   */
  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    JsonNode raiz;
    try {
      raiz = json.readTree(corpo);
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso da Cielo ilegível.");
    }
    String pedido = primeiroNaoVazio(CieloProvedor.texto(raiz, "order_id"), CieloProvedor.texto(raiz, "id"));
    String tipo = raiz.has("order_id") ? "transacao" : "pedido";
    if (pedido == null) return new AvisoDaOperadora(tipo + ":" + System.currentTimeMillis(), tipo, null, null);
    StatusNaOperadora status = consultar(conta, pedido);
    // A Cielo repete o aviso: o evento e o pedido + o status (+ o estornado), para nao aplicar duas vezes.
    return new AvisoDaOperadora(pedido + ":" + status.status() + ":" + status.valorEstornado(), tipo, pedido, status);
  }

  // ---- corpos e status ------------------------------------------------------------------------

  /** Um item so, com o valor da cobranca: a comanda ja esta no Azzo, a maquininha so cobra. */
  static Map<String, Object> pedidoDaComanda(PedidoDeCobranca pedido) {
    long centavos = CieloProvedor.centavos(pedido.valor());
    String nome = pedido.descricao() == null || pedido.descricao().isBlank() ? "Comanda" : pedido.descricao().trim();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("sku", "AZZO-COMANDA");
    item.put("name", limitar(nome, 60));
    item.put("unit_price", centavos);
    item.put("quantity", 1);
    item.put("unit_of_measure", "EACH");
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("number", pedido.cobrancaId());
    corpo.put("reference", "Azzo " + pedido.cobrancaId().substring(0, Math.min(8, pedido.cobrancaId().length())));
    corpo.put("status", "DRAFT");
    corpo.put("items", List.of(item));
    corpo.put("notes", limitar(nome, 100));
    corpo.put("price", centavos);
    // Parcelas: so credito parcelado (0 = a vista). Nos modelos antigos (L300 V3) a Cielo ignora.
    corpo.put("installments", "CREDITO".equals(pedido.meio()) && pedido.parcelas() > 1 ? pedido.parcelas() : 0);
    if (pedido.terminalSerial() != null && !pedido.terminalSerial().isBlank()) {
      corpo.put("terminal_number", pedido.terminalSerial().trim());
    }
    return corpo;
  }

  /**
   * Pedido + transacoes da Cielo → status do Azzo. Vale o dinheiro (transacoes CONFIRMED), e nao so
   * o status do pedido: pagamento confirmado = PAGA; cancelamento confirmado na maquininha =
   * ESTORNADA (ou parcial); pedido fechado/cancelado sem pagamento = CANCELADA; o resto espera.
   */
  static StatusNaOperadora status(JsonNode pedido, JsonNode transacoesDaLista) {
    List<JsonNode> transacoes = new ArrayList<>();
    JsonNode fonte = transacoesDaLista != null && transacoesDaLista.isArray() && transacoesDaLista.size() > 0
        ? transacoesDaLista : pedido == null ? null : pedido.path("transactions");
    if (fonte != null && fonte.isArray()) fonte.forEach(transacoes::add);

    long pago = 0;
    long cancelado = 0;
    JsonNode pagamento = null;
    for (JsonNode t : transacoes) {
      if (!"CONFIRMED".equalsIgnoreCase(t.path("status").asText())) continue;
      long valor = t.path("amount").asLong(0);
      if ("CANCELLATION".equalsIgnoreCase(t.path("transaction_type").asText())) {
        cancelado += valor;
      } else {
        pago += valor;
        pagamento = t;
      }
    }
    String situacao = pedido == null ? "" : pedido.path("status").asText("").toUpperCase();
    long preco = pedido == null ? 0 : pedido.path("price").asLong(0);

    Status status;
    if (pago > 0 && cancelado > 0) {
      status = cancelado >= pago ? Status.ESTORNADA : Status.ESTORNO_PARCIAL;
    } else if (pago > 0 && (pago >= preco || "PAID".equals(situacao) || "CLOSED".equals(situacao))) {
      status = Status.PAGA;
    } else if ("CANCELED".equals(situacao) || "CANCELLED".equals(situacao) || "CLOSED".equals(situacao)) {
      status = Status.CANCELADA;
    } else {
      status = Status.AGUARDANDO;
    }

    JsonNode cartao = pagamento == null ? null : pagamento.path("card");
    return new StatusNaOperadora(status,
        pago > 0 ? BigDecimal.valueOf(pago, 2) : null,
        null, null, null,
        pagamento == null ? null : CieloProvedor.texto(pagamento, "number"),
        pagamento == null ? null : CieloProvedor.texto(pagamento, "authorization_code"),
        cartao == null ? null : CieloProvedor.texto(cartao, "brand"),
        cartao == null ? null : CieloProvedor.texto(cartao, "mask"),
        cancelado > 0 ? BigDecimal.valueOf(cancelado, 2) : null,
        status == Status.CANCELADA ? "Pedido fechado na Cielo sem pagamento." : null);
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private boolean apagarSemFalhar(RestClient cielo, String id) {
    try {
      cielo.delete().uri("/orders/{id}", id).retrieve().toBodilessEntity();
      return true;
    } catch (RestClientException e) {
      return false;
    }
  }

  private RestClient cliente(Conta conta) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder()
        .requestFactory(fabrica)
        .baseUrl(conta.producao() ? "https://api.cielo.com.br/order-management/v1"
            : "https://api.cielo.com.br/sandbox-lio/order-management/v1")
        .defaultHeader("client-id", valorOuVazio(conta.credencial("clientId")))
        .defaultHeader("access-token", valorOuVazio(conta.credencial("accessToken")))
        .defaultHeader("merchant-id", valorOuVazio(conta.credencial("merchantId")))
        .defaultHeader("User-Agent", "AzzoAgendaPro/1.0")
        .build();
  }

  private static String mensagem(RestClientResponseException e, String acao) {
    try {
      JsonNode corpo = new ObjectMapper().readTree(e.getResponseBodyAsString());
      String detalhe = primeiroNaoVazio(CieloProvedor.texto(corpo, "message"), CieloProvedor.texto(corpo, "error"));
      if (detalhe != null) return "Cielo: " + detalhe;
    } catch (Exception ignorado) {
      // cai na mensagem generica
    }
    return "A Cielo não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  private static String valorOuVazio(String s) {
    return s == null ? "" : s.trim();
  }

  private static String primeiroNaoVazio(String... valores) {
    for (String v : valores) if (v != null && !v.isBlank()) return v;
    return null;
  }

  private static String limitar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
