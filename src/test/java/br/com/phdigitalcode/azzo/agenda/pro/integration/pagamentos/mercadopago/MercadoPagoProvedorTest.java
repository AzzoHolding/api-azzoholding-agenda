package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class MercadoPagoProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final MercadoPagoProvedor mp = new MercadoPagoProvedor(json);

  private ProvedorDePagamento.Conta conta(String segredo) {
    return new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX",
        segredo == null ? Map.of("accessToken", "APP_USR-x") : Map.of("accessToken", "APP_USR-x", "webhookSecret", segredo),
        "https://x/webhook");
  }

  private static final String ORDER_PROCESSADA = """
      {"action":"order.processed","type":"order","data":{"id":"ORD01JY0PGGPZ4DBV73E2PXRBCQ84","status":"processed",
       "status_detail":"accredited","total_paid_amount":"5.00","version":3,
       "transactions":{"payments":[{"amount":"5.00","paid_amount":"5.00","reference":{"id":"115019989861"},
       "payment_method":{"id":"master","installments":1,"type":"credit_card"},"status":"processed"}]}}}""";

  @Test
  void statusDaOrderViraStatusDoAzzoComNsuEBandeira() throws Exception {
    var pago = MercadoPagoProvedor.status(json.readTree(ORDER_PROCESSADA).path("data"));
    assertThat(pago.status()).isEqualTo(Status.PAGA);
    assertThat(pago.valorPago()).isEqualByComparingTo("5.00");
    assertThat(pago.nsu()).isEqualTo("115019989861");
    assertThat(pago.bandeira()).isEqualTo("Mastercard");

    assertThat(MercadoPagoProvedor.status(json.readTree("{\"status\":\"at_terminal\"}")).status()).isEqualTo(Status.AGUARDANDO);
    assertThat(MercadoPagoProvedor.status(json.readTree("{\"status\":\"action_required\",\"status_detail\":\"waiting_transfer\"}")).status())
        .isEqualTo(Status.AGUARDANDO);
    assertThat(MercadoPagoProvedor.status(json.readTree("{\"status\":\"expired\"}")).status()).isEqualTo(Status.EXPIRADA);
    assertThat(MercadoPagoProvedor.status(json.readTree("{\"status\":\"refunded\"}")).status()).isEqualTo(Status.ESTORNADA);
    var recusada = MercadoPagoProvedor.status(json.readTree(
        "{\"status\":\"failed\",\"transactions\":{\"payments\":[{\"status_detail\":\"rejected_by_bank\"}]}}"));
    assertThat(recusada.status()).isEqualTo(Status.RECUSADA);
    assertThat(recusada.motivo()).isEqualTo("rejected_by_bank");
  }

  @Test
  void avisoComAssinaturaCertaPassaEErradaOuAusenteERecusado() {
    String segredo = "segredo-do-painel";
    String ts = "1742505638683";
    String requestId = "2066ca19-c6f1-498a-be75-1923005edd06";
    String v1 = MercadoPagoProvedor.hmac(segredo, "id:ord01jy0pggpz4dbv73e2pxrbcq84;request-id:" + requestId + ";ts:" + ts + ";");
    Map<String, String> cabecalhos = Map.of(
        "x-signature", "ts=" + ts + ",v1=" + v1, "x-request-id", requestId,
        "query.data.id", "ORD01JY0PGGPZ4DBV73E2PXRBCQ84", "query.type", "order");

    var aviso = mp.lerAviso(conta(segredo), cabecalhos, ORDER_PROCESSADA);
    assertThat(aviso.idNaOperadora()).isEqualTo("ORD01JY0PGGPZ4DBV73E2PXRBCQ84");
    assertThat(aviso.idDoEvento()).isEqualTo("order.processed:ORD01JY0PGGPZ4DBV73E2PXRBCQ84:3");
    assertThat(aviso.status().status()).isEqualTo(Status.PAGA);

    Map<String, String> adulterado = new java.util.HashMap<>(cabecalhos);
    adulterado.put("x-signature", "ts=" + ts + ",v1=" + "0".repeat(64));
    assertThatThrownBy(() -> mp.lerAviso(conta(segredo), adulterado, ORDER_PROCESSADA))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    assertThatThrownBy(() -> mp.lerAviso(conta(segredo), Map.of("query.data.id", "ORD1"), ORDER_PROCESSADA))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class).hasMessageContaining("sem assinatura");

    // Sem chave secreta configurada, vale o token da URL (ja conferido antes).
    assertThat(mp.lerAviso(conta(null), Map.of(), ORDER_PROCESSADA).status().status()).isEqualTo(Status.PAGA);
  }

  @Test
  void avisoDeOutroTemaNaoApontaParaCobranca() {
    var aviso = mp.lerAviso(conta(null), Map.of(), "{\"action\":\"claim.created\",\"type\":\"claims\",\"data\":{\"id\":\"9\"}}");
    assertThat(aviso.idNaOperadora()).isNull();
  }

  @Test
  void ordersDaPointDoPixEDoLinkNoFormatoDaApi() {
    var point = MercadoPagoProvedor.orderPoint(new ProvedorDePagamento.PedidoDeCobranca(
        "11111111-2222-3333-4444-555555555555", "CREDITO", new BigDecimal("80"), 3, "Comanda 12", null,
        "NEWLAND_N950__N950NCB801293324", null));
    assertThat(point).containsEntry("type", "point").containsEntry("external_reference", "11111111-2222-3333-4444-555555555555");
    assertThat(point.toString()).contains("terminal_id=NEWLAND_N950__N950NCB801293324").contains("amount=80.00")
        .contains("default_type=credit_card").contains("default_installments=3");

    var debito = MercadoPagoProvedor.orderPoint(new ProvedorDePagamento.PedidoDeCobranca(
        "11111111-2222-3333-4444-555555555555", "DEBITO", new BigDecimal("80"), 3, "x", null, "T__1", null));
    assertThat(debito.toString()).contains("default_type=debit_card").doesNotContain("default_installments");

    var pix = MercadoPagoProvedor.orderPix(new ProvedorDePagamento.PedidoDeCobranca(
        "11111111-2222-3333-4444-555555555555", "PIX", new BigDecimal("50.5"), 1, "Comanda 12",
        new ProvedorDePagamento.Pagador(null, "Ana Souza", null, null, "123.456.789-09"), null,
        Instant.now().plusSeconds(1800)));
    assertThat(pix).containsEntry("type", "online").containsEntry("total_amount", "50.50");
    assertThat(pix.toString()).contains("id=pix").contains("type=bank_transfer")
        .contains("@clientes.azzoholding.com.br").contains("first_name=Ana").contains("number=12345678909");

    var link = MercadoPagoProvedor.orderLink(new ProvedorDePagamento.PedidoDeCobranca(
        "11111111-2222-3333-4444-555555555555", "LINK", new BigDecimal("10"), 1, "Sinal", null, null, null));
    assertThat(link).containsEntry("processing_mode", "manual");
    assertThat((List<?>) link.get("items")).hasSize(1);
  }

  @Test
  void cartaoSemMaquininhaEEstornoParcialNaoSaoFeitos() {
    var semTerminal = new ProvedorDePagamento.PedidoDeCobranca("c", "CREDITO", BigDecimal.TEN, 1, "x", null, null, null);
    assertThatThrownBy(() -> mp.criarCobranca(conta(null), semTerminal)).isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
    assertThatThrownBy(() -> mp.estornar(conta(null), "ORD1", BigDecimal.ONE)).isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
  }
}
