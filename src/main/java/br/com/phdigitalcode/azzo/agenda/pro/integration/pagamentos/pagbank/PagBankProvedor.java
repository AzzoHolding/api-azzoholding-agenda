package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pagbank;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HexFormat;
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
 * PagBank na conta do proprio salao, pela API de Pedidos ({@code /orders}, token Bearer da conta).
 *
 * <p>Nesta fase: <b>Pix</b> ({@code qr_codes} no pedido; o copia e cola vem em
 * {@code qr_codes[].text}), consulta, cancelamento/estorno ({@code POST /charges/{id}/cancel},
 * total ou parcial). O pedido pago ganha um {@code charge} com status {@code PAID}.
 *
 * <p>Aviso: o PagBank manda o pedido inteiro para a {@code notification_urls} e assina com
 * {@code x-authenticity-token} = SHA-256 de {@code "<token>-<corpo>"}. A assinatura e conferida
 * quando vem; o status e sempre reconsultado na API.
 */
@Component
public class PagBankProvedor implements ProvedorDePagamento {

  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public PagBankProvedor(ObjectMapper json) {
    this.json = json;
  }

  @Override
  public String codigo() {
    return "PAGBANK";
  }

  @Override
  public String nome() {
    return "PagBank";
  }

  @Override
  public Set<Capacidade> capacidades() {
    return EnumSet.of(Capacidade.PIX, Capacidade.ESTORNO, Capacidade.ESTORNO_PARCIAL);
  }

  @Override
  public List<CampoDeCredencial> camposDeCredencial() {
    return List.of(new CampoDeCredencial("token", "Token da conta (PagBank → Vendas → Integrações → Gerar token)", true));
  }

  /** Consulta um pedido que nao existe: 404 = token aceito; 401 = recusado. */
  @Override
  public ResultadoValidacao validarCredenciais(Conta conta) {
    try {
      cliente(conta).get().uri("/orders/{id}", "ORDE_00000000-0000-0000-0000-000000000000").retrieve().toBodilessEntity();
      return new ResultadoValidacao(true, "Conta PagBank conectada.");
    } catch (RestClientResponseException e) {
      int codigo = e.getStatusCode().value();
      if (codigo == 404 || codigo == 400) return new ResultadoValidacao(true, "Conta PagBank conectada.");
      return new ResultadoValidacao(false, codigo == 401 || codigo == 403
          ? "O PagBank recusou o token. Confira o ambiente (teste ou produção) e se copiou o token inteiro."
          : "O PagBank respondeu " + codigo + ". Tente de novo em instantes.");
    } catch (RestClientException e) {
      return new ResultadoValidacao(false, "Não foi possível falar com o PagBank agora. Tente de novo em instantes.");
    }
  }

  @Override
  public CobrancaCriada criarCobranca(Conta conta, PedidoDeCobranca pedido) {
    if (!"PIX".equals(pedido.meio())) throw new NaoSuportado(codigo(), "cobrança por " + pedido.meio());
    try {
      JsonNode criado = cliente(conta).post().uri("/orders")
          .header("x-idempotency-key", pedido.cobrancaId())
          .contentType(MediaType.APPLICATION_JSON).body(pedidoPix(pedido, conta.urlDeAviso()))
          .retrieve().body(JsonNode.class);
      JsonNode qr = criado.path("qr_codes").path(0);
      String copiaECola = texto(qr, "text");
      if (copiaECola == null) throw new FalhaNaOperadora("O PagBank não gerou o Pix. Confira se o Pix está liberado na conta.", null);
      return new CobrancaCriada(texto(criado, "id"), Status.AGUARDANDO, copiaECola, null, pedido.expiraEm());
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "criar o Pix"), e);
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível falar com o PagBank agora. Tente de novo em instantes.", e);
    }
  }

  @Override
  public StatusNaOperadora consultar(Conta conta, String idNaOperadora) {
    try {
      return status(cliente(conta).get().uri("/orders/{id}", idNaOperadora).retrieve().body(JsonNode.class));
    } catch (RestClientException e) {
      throw new FalhaNaOperadora("Não foi possível consultar a cobrança no PagBank agora.", e);
    }
  }

  /** Pix nao pago no PagBank nao tem cancelamento pela API: ele expira. So o pago e estornado. */
  @Override
  public void cancelar(Conta conta, String idNaOperadora) {
    if (consultar(conta, idNaOperadora).status() == Status.PAGA) {
      throw new FalhaNaOperadora("O Pix já foi pago no PagBank: use o estorno.", null);
    }
  }

  @Override
  public StatusNaOperadora estornar(Conta conta, String idNaOperadora, BigDecimal valor) {
    try {
      JsonNode pedido = cliente(conta).get().uri("/orders/{id}", idNaOperadora).retrieve().body(JsonNode.class);
      JsonNode pago = null;
      for (JsonNode charge : pedido.path("charges")) if ("PAID".equals(texto(charge, "status"))) pago = charge;
      if (pago == null) throw new FalhaNaOperadora("O PagBank não tem pagamento confirmado para estornar nesta cobrança.", null);
      long centavos = valor == null ? pago.path("amount").path("value").asLong() : centavos(valor);
      cliente(conta).post().uri("/charges/{id}/cancel", texto(pago, "id"))
          .header("x-idempotency-key", "estornar-" + texto(pago, "id") + "-" + centavos)
          .contentType(MediaType.APPLICATION_JSON).body(Map.of("amount", Map.of("value", centavos)))
          .retrieve().toBodilessEntity();
      return new StatusNaOperadora(valor == null ? Status.ESTORNADA : Status.ESTORNO_PARCIAL, null, null, null, null,
          null, null, null, null, BigDecimal.valueOf(centavos, 2), null);
    } catch (RestClientResponseException e) {
      throw new FalhaNaOperadora(mensagem(e, "estornar"), e);
    }
  }

  @Override
  public AvisoDaOperadora lerAviso(Conta conta, Map<String, String> cabecalhos, String corpo) {
    String assinatura = cabecalhos.get("x-authenticity-token");
    if (assinatura != null && !assinatura.isBlank()) {
      String esperado = sha256(conta.credencial("token") + "-" + corpo);
      if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
          assinatura.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
        throw new AvisoInvalido("Assinatura do aviso do PagBank não confere.");
      }
    }
    JsonNode raiz;
    try {
      raiz = json.readTree(corpo);
    } catch (Exception e) {
      throw new AvisoInvalido("Aviso do PagBank ilegível.");
    }
    String idDoPedido = texto(raiz, "id");
    if (idDoPedido == null || !idDoPedido.startsWith("ORDE_")) {
      return new AvisoDaOperadora("pagbank:" + sha256(corpo), "outro", null, null);
    }
    StatusNaOperadora status = consultar(conta, idDoPedido);
    return new AvisoDaOperadora(idDoPedido + ":" + status.status(), "order", idDoPedido, status);
  }

  // ---- corpos e status -----------------------------------------------------------------------

  static Map<String, Object> pedidoPix(PedidoDeCobranca pedido, String urlDeAviso) {
    Pagador p = pedido.pagador();
    if (p == null || p.documento() == null || p.documento().isBlank()) {
      throw new FalhaNaOperadora("O Pix pelo PagBank exige cliente com CPF ou CNPJ no cadastro.", null);
    }
    long valor = centavos(pedido.valor());
    Map<String, Object> cliente = new LinkedHashMap<>();
    cliente.put("name", limitar(p.nome() == null || p.nome().isBlank() ? "Cliente" : p.nome().trim(), 120));
    cliente.put("email", p.email() == null || p.email().isBlank()
        ? "pagamento-" + pedido.cobrancaId().substring(0, 8) + "@clientes.azzoholding.com.br" : p.email().trim());
    cliente.put("tax_id", p.documento().replaceAll("\\D", ""));
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("reference_id", "azzo");
    item.put("name", limitar(pedido.descricao() == null ? "Pagamento" : pedido.descricao(), 100));
    item.put("quantity", 1);
    item.put("unit_amount", valor);
    Map<String, Object> qr = new LinkedHashMap<>();
    qr.put("amount", Map.of("value", valor));
    Instant expira = pedido.expiraEm() == null ? Instant.now().plus(Duration.ofMinutes(30)) : pedido.expiraEm();
    qr.put("expiration_date", OffsetDateTime.ofInstant(expira, ZoneOffset.of("-03:00"))
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("reference_id", pedido.cobrancaId());
    corpo.put("customer", cliente);
    corpo.put("items", List.of(item));
    corpo.put("qr_codes", List.of(qr));
    if (urlDeAviso != null && urlDeAviso.startsWith("https://")) corpo.put("notification_urls", List.of(urlDeAviso));
    return corpo;
  }

  /** Pedido → status do Azzo, pelos charges: PAID paga; CANCELED com valor devolvido, estornada. */
  static StatusNaOperadora status(JsonNode pedido) {
    long valor = 0;
    for (JsonNode q : pedido.path("qr_codes")) valor += q.path("amount").path("value").asLong(0);
    long pago = 0;
    long devolvido = 0;
    JsonNode ultimo = null;
    for (JsonNode charge : pedido.path("charges")) {
      ultimo = charge;
      JsonNode resumo = charge.path("amount").path("summary");
      pago += resumo.path("paid").asLong("PAID".equals(texto(charge, "status")) ? charge.path("amount").path("value").asLong(0) : 0);
      devolvido += resumo.path("refunded").asLong(0);
    }
    Status status;
    if (pago > 0 && devolvido >= pago) status = Status.ESTORNADA;
    else if (pago > 0 && devolvido > 0) status = Status.ESTORNO_PARCIAL;
    else if (pago > 0 && (valor == 0 || pago >= valor)) status = Status.PAGA;
    else if (ultimo != null && "DECLINED".equals(texto(ultimo, "status"))) status = Status.RECUSADA;
    else status = Status.AGUARDANDO;
    String nsu = ultimo == null ? null : texto(ultimo.path("payment_response"), "reference");
    return new StatusNaOperadora(status, pago > 0 ? BigDecimal.valueOf(pago, 2) : null, null, null, null, nsu, null, null,
        null, devolvido > 0 ? BigDecimal.valueOf(devolvido, 2) : null,
        status == Status.RECUSADA ? texto(ultimo.path("payment_response"), "message") : null);
  }

  // ---- apoio ---------------------------------------------------------------------------------

  private RestClient cliente(Conta conta) {
    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(20));
    return RestClient.builder()
        .requestFactory(fabrica)
        .baseUrl(conta.producao() ? "https://api.pagseguro.com" : "https://sandbox.api.pagseguro.com")
        .defaultHeader("Authorization", "Bearer " + (conta.credencial("token") == null ? "" : conta.credencial("token").trim()))
        .defaultHeader("User-Agent", "AzzoAgendaPro/1.0")
        .build();
  }

  /** Erro do PagBank: {@code {"error_messages":[{"code":"...","description":"..."}]}}. */
  private static String mensagem(RestClientResponseException e, String acao) {
    try {
      JsonNode corpo = new ObjectMapper().readTree(e.getResponseBodyAsString());
      JsonNode erro = corpo.path("error_messages").path(0);
      String detalhe = texto(erro, "description");
      if (detalhe != null) return "PagBank: " + detalhe + (texto(erro, "parameter_name") == null ? "" : " (" + texto(erro, "parameter_name") + ")");
    } catch (Exception ignorado) {
      // cai na mensagem generica
    }
    return "O PagBank não conseguiu " + acao + " (código " + e.getStatusCode().value() + ").";
  }

  static String sha256(String texto) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static long centavos(BigDecimal valor) {
    return valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
  }

  static String texto(JsonNode no, String campo) {
    if (no == null) return null;
    JsonNode v = no.path(campo);
    return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
  }

  private static String limitar(String valor, int maximo) {
    return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
  }
}
