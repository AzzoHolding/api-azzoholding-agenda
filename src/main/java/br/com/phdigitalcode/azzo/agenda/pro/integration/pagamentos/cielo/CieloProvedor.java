package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.cielo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
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
 * Cielo na conta do proprio salao, pela API E-commerce 3.0 (MerchantId + MerchantKey).
 *
 * <p>Nesta fase so o <b>Pix</b>: {@code POST /1/sales/} com {@code Payment.Type=Pix} devolve o
 * copia e cola ({@code QrCodeString}); a consulta e pelo host de consultas ({@code apiquery}); o
 * estorno e {@code PUT /1/sales/{PaymentId}/void?amount=}. O link (Super Link) e a maquininha (LIO)
 * usam outras APIs e outras credenciais da Cielo — ficam para quando o salao tiver o cadastro de
 * desenvolvedor (ver {@code PLANO-PAGAMENTOS.md}).
 *
 * <p>O aviso ("Post de Notificacao") traz so {@code PaymentId} e {@code ChangeType}: o status vem
 * sempre da consulta. A Cielo nao assina o aviso, mas deixa cadastrar um cabecalho fixo no painel:
 * com o campo "Valor do cabeçalho X-Azzo-Aviso" preenchido, o aviso sem ele e recusado.
 */
@Component
public class CieloProvedor implements ProvedorDePagamento {

  static final String CABECALHO_DO_AVISO = "x-azzo-aviso";

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public CieloProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return "CIELO";
  }

  @Override
  public String nome() {
    return "Cielo";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.ESTORNO, Capacidade.ESTORNO_PARCIAL);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(
        new CampoDeCredencial("merchantId", "MerchantId (credenciais da API E-commerce Cielo)", false),
        new CampoDeCredencial("merchantKey", "MerchantKey", true),
        new CampoDeCredencial("segredoDoAviso",
            "Valor do cabeçalho X-Azzo-Aviso (cadastre o mesmo no painel da Cielo, em URL de Notificação) — recomendado",
            true, false));
  }

  /** Consulta um pagamento que nao existe: 404 = credencial aceita; 401/403 = recusada. */
  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      consulta(conta).get().uri("/1/sales/{id}", "00000000-0000-0000-0000-000000000000").retrieve().toBodilessEntity();
      return new ResultadoValidacao(true, "Conta Cielo conectada.");
    } catch (RestClientResponseException e) {
      int codigo = e.getStatusCode().value();
      if (codigo == 404 || codigo == 400) return new ResultadoValidacao(true, "Conta Cielo conectada.");
      return new ResultadoValidacao(false, codigo == 401 || codigo == 403
          ? "A Cielo recusou o MerchantId/MerchantKey. Confira o ambiente (teste ou produção) e as duas chaves."
          : "A Cielo respondeu " + codigo + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com a Cielo agora. Tente de novo em instantes.");
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"PIX".equals(pedido.meio())) throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    try {
      JsonNode venda = transacional(conta).post().uri("/1/sales/")
          .header("RequestId", pedido.cobrancaId())
          .contentType(MediaType.APPLICATION_JSON).body(vendaPix(pedido, conta.producao()))
          .retrieve().body(JsonNode.class);
      JsonNode pagamento = venda.path("Payment");
      String copiaECola = texto(pagamento, "QrCodeString");
      if (copiaECola == null) {
        throw new FalhaNaOperadora("A Cielo não gerou o Pix: "
            + primeiroNaoVazio(texto(pagamento, "ReturnMessage"), "confira se o Pix está habilitado na sua loja Cielo."), null);
      }
      return new CobrancaCriada(texto(pagamento, "PaymentId"), Status.AGUARDANDO, copiaECola, null, pedido.expiraEm());
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "criar o Pix"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com a Cielo agora. Tente de novo em instantes.", e);
    }
  }

  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    try {
      return status(consulta(conta).get().uri("/1/sales/{id}", idNaOperadora).retrieve().body(JsonNode.class));
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança na Cielo agora.", e);
    }
  }

  /** Pix nao pago na Cielo nao tem cancelamento: ele expira sozinho. Aqui so a marca local muda. */
  @Override
  public void cancelar(Conta conta, String idNaOperadora) {
    StatusNaOperadora atual = consultar(conta, idNaOperadora);
    if (atual.status() == Status.PAGA) {
      throw new FalhaNaOperadora("O Pix já foi pago na Cielo: use o estorno.", null);
    }
  }

  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    try {
      JsonNode resposta = transacional(conta).put()
          .uri(u -> {
            u.path("/1/sales/{id}/void");
            if (valor != null) u.queryParam("amount", centavos(valor));
            return u.build(idNaOperadora);
          })
          .retrieve().body(JsonNode.class);
      int s = resposta == null ? -1 : resposta.path("Status").asInt(-1);
      if (s != 10 && s != 11 && s != 2) {
        throw new FalhaNaOperadora("A Cielo não confirmou o estorno: "
            + primeiroNaoVazio(texto(resposta, "ReturnMessage"), "tente pelo painel da Cielo."), null);
      }
      return new StatusNaOperadora(valor == null ? Status.ESTORNADA : Status.ESTORNO_PARCIAL, null, null, null, null,
          null, null, null, null, valor, null);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "estornar"), e);
    }
  }

  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    String segredo = conta.credencial("segredoDoAviso");
    if (segredo != null && !segredo.isBlank()) {
      String recebido = cabecalhos.get(CABECALHO_DO_AVISO);
      if (recebido == null || !MessageDigest.isEqual(segredo.trim().getBytes(StandardCharsets.UTF_8),
          recebido.trim().getBytes(StandardCharsets.UTF_8))) {
        throw new AvisoInvalido("Aviso da Cielo sem o cabeçalho combinado.");
      }
    }
    JsonNode raiz;
    try {
      raiz = json.readTree(corpo);
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso da Cielo ilegível.");
    }
    String paymentId = texto(raiz, "PaymentId");
    String tipo = "ChangeType:" + raiz.path("ChangeType").asText("");
    if (paymentId == null) return new AvisoDaOperadora(tipo + ":" + Instant.now().toEpochMilli(), tipo, null, null);
    StatusNaOperadora status = consultar(conta, paymentId);
    // A Cielo repete o aviso; o evento e o par pagamento + status, para nao aplicar duas vezes.
    return new AvisoDaOperadora(paymentId + ":" + tipo + ":" + status.status(), tipo, paymentId, status);
  }

  // ---- corpos --------------------------------------------------------------------------------

  static Map<String, Object> vendaPix(PedidoDeCobranca pedido, boolean producao) {
    Pagador p = pedido.pagador();
    if (p == null || p.documento() == null || p.documento().isBlank()) {
      throw new FalhaNaOperadora("O Pix pela Cielo exige cliente com CPF ou CNPJ no cadastro.", null);
    }
    String doc = p.documento().replaceAll("\\D", "");
    Map<String, Object> cliente = new LinkedHashMap<>();
    cliente.put("Name", limitar(p.nome() == null || p.nome().isBlank() ? "Cliente" : p.nome().trim(), 255));
    cliente.put("Identity", doc);
    cliente.put("IdentityType", doc.length() > 11 ? "CNPJ" : "CPF");
    Map<String, Object> pagamento = new LinkedHashMap<>();
    pagamento.put("Type", "Pix");
    // O provedor novo (Cielo2) so existe em producao; no sandbox vale o padrao.
    if (producao) pagamento.put("Provider", "Cielo2");
    pagamento.put("Amount", centavos(pedido.valor()));
    pagamento.put("QrCode", Map.of("Expiration", segundos(pedido.expiraEm(), Duration.ofMinutes(30))));
    Map<String, Object> venda = new LinkedHashMap<>();
    // MerchantOrderId tem ate 50 caracteres: o id da cobranca (36) cabe.
    venda.put("MerchantOrderId", pedido.cobrancaId());
    venda.put("Customer", cliente);
    venda.put("Payment", pagamento);
    return venda;
  }

  /**
   * {@code Payment.Status} da Cielo → status do Azzo: 2 pago, 3 negado, 10 cancelado, 11 estornado,
   * 13 abortado; 0 e 12 esperando.
   */
  static StatusNaOperadora status(JsonNode venda) {
    JsonNode p = venda == null ? null : venda.path("Payment");
    int s = p == null ? -1 : p.path("Status").asInt(-1);
    Status status = switch (s) {
      case 2 -> Status.PAGA;
      case 3 -> Status.RECUSADA;
      case 10 -> p.has("CapturedAmount") || p.has("CapturedDate") ? Status.ESTORNADA : Status.CANCELADA;
      case 11 -> Status.ESTORNADA;
      case 13 -> Status.CANCELADA;
      default -> Status.AGUARDANDO;
    };
    BigDecimal pago = status == Status.PAGA && p.has("Amount") ? BigDecimal.valueOf(p.path("Amount").asLong(), 2) : null;
    String motivo = status == Status.RECUSADA ? texto(p, "ReturnMessage") : null;
    return new StatusNaOperadora(status, pago, null, null, null,
        p == null ? null : primeiroNaoVazio(texto(p, "ProofOfSale"), texto(p, "Tid")),
        p == null ? null : texto(p, "AuthorizationCode"), null, null,
        status == Status.ESTORNADA && p.has("VoidedAmount") ? BigDecimal.valueOf(p.path("VoidedAmount").asLong(), 2) : null,
        motivo);
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private RestClient transacional(Conta conta) {
    return cliente(conta, conta.producao() ? "https://api.cieloecommerce.cielo.com.br"
        : "https://apisandbox.cieloecommerce.cielo.com.br");
  }

  private RestClient consulta(Conta conta) {
    return cliente(conta, conta.producao() ? "https://apiquery.cieloecommerce.cielo.com.br"
        : "https://apiquerysandbox.cieloecommerce.cielo.com.br");
  }

  private RestClient cliente(Conta conta, String base) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder()
        .requestFactory(fabrica)
        .baseUrl(base)
        .defaultHeader("MerchantId", valorOuVazio(conta.credencial("merchantId")))
        .defaultHeader("MerchantKey", valorOuVazio(conta.credencial("merchantKey")))
        .defaultHeader("User-Agent", "AzzoAgendaPro/1.0")
        .build();
  }

  /** A Cielo responde erro como lista: {@code [{"Code":126,"Message":"..."}]}. */
  private static String mensagem(RestClientResponseException e, String acao) {
    try {
      JsonNode corpo = new ObjectMapper().readTree(e.getResponseBodyAsString());
      JsonNode erro = corpo.isArray() && corpo.size() > 0 ? corpo.get(0) : corpo;
      String detalhe = texto(erro, "Message");
      if (detalhe != null) return "Cielo: " + detalhe;
    } catch (Exception ignorado) {
      // cai na mensagem generica
    }
    return "A Cielo não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  static long centavos(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
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
