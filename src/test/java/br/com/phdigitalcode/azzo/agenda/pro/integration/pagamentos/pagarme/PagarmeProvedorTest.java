package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pagarme;

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

class PagarmeProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final PagarmeProvedor pagarme = new PagarmeProvedor(json);

  private static final String ID = "11111111-2222-3333-4444-555555555555";

  private ProvedorDePagamento.Conta conta(String parceiro) {
    return new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX",
        parceiro == null ? Map.of("secretKey", "sk_test_x") : Map.of("secretKey", "sk_test_x", "serviceRefererName", parceiro),
        "https://x/webhook");
  }

  private static ProvedorDePagamento.PedidoDeCobranca pedido(String meio, ProvedorDePagamento.Pagador pagador, String serial) {
    return new ProvedorDePagamento.PedidoDeCobranca(ID, meio, new BigDecimal("80.5"), 3, "Comanda 12", pagador, serial,
        Instant.now().plusSeconds(1800));
  }

  @Test
  void pedidoDaMaquininhaStoneNoFormatoDoStoneConnect() {
    var corpo = PagarmeProvedor.pedidoMaquininha(pedido("CREDITO", null, "6C582505"));
    assertThat(corpo).containsEntry("closed", false).containsEntry("code", ID);
    @SuppressWarnings("unchecked")
    var poi = (Map<String, Object>) corpo.get("poi_payment_settings");
    assertThat(poi).containsEntry("visible", true).containsEntry("devices_serial_number", List.of("6C582505"));
    assertThat(poi.get("payment_setup").toString()).contains("type=credit").contains("installments=3")
        .contains("installment_type=merchant");
    assertThat(corpo.get("items").toString()).contains("amount=8050");
    // Sem cliente, vai nome generico e e-mail tecnico (o Stone Connect exige os dois).
    assertThat(corpo.get("customer").toString()).contains("name=Cliente").contains("@clientes.azzoholding.com.br");

    var debito = PagarmeProvedor.pedidoMaquininha(pedido("DEBITO", null, "6C582505"));
    assertThat(debito.get("poi_payment_settings").toString()).contains("type=debit").contains("installments=1");
  }

  @Test
  void pixExigeDocumentoEMandaTelefoneSemDdi() {
    assertThatThrownBy(() -> PagarmeProvedor.pedidoPix(pedido("PIX", null, null)))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("CPF");
    var pix = PagarmeProvedor.pedidoPix(pedido("PIX",
        new ProvedorDePagamento.Pagador(null, "Ana Souza", "ana@x.com", "+55 (11) 98765-4321", "123.456.789-09"), null));
    String texto = pix.toString();
    assertThat(texto).contains("payment_method=pix").contains("document=12345678909").contains("type=individual")
        .contains("area_code=11").contains("number=987654321").contains("expires_in=");
  }

  @Test
  void linkComUmPagamentoSoECodigoDaCobranca() {
    var link = PagarmeProvedor.link(pedido("LINK", null, null));
    assertThat(link).containsEntry("type", "order").containsEntry("order_code", ID).containsEntry("max_paid_sessions", 1);
    assertThat(link.get("payment_settings").toString()).contains("credit_card").contains("pix").contains("total=8050");
  }

  @Test
  void statusDoPedidoPeloCharge() throws Exception {
    // Maquininha: o pedido segue "pending" ate o Azzo fechar; o charge pago e o que vale.
    var pago = PagarmeProvedor.statusDoPedido(json.readTree("""
        {"id":"or_1","status":"pending","closed":false,"amount":8050,
         "charges":[{"id":"ch_1","code":"123456","status":"paid","amount":8050,"paid_amount":8050,
           "metadata":{"scheme_name":"MasterCard","authorization_code":"A1B2","account_funding_source":"Credit"}}]}"""));
    assertThat(pago.status()).isEqualTo(Status.PAGA);
    assertThat(pago.valorPago()).isEqualByComparingTo("80.50");
    assertThat(pago.nsu()).isEqualTo("123456");
    assertThat(pago.autorizacao()).isEqualTo("A1B2");
    assertThat(pago.bandeira()).isEqualTo("Mastercard");

    var debito = PagarmeProvedor.statusDoPedido(json.readTree("""
        {"status":"pending","amount":100,"charges":[{"status":"paid","amount":100,
         "metadata":{"scheme_name":"Visa","account_funding_source":"Debit"}}]}"""));
    assertThat(debito.bandeira()).isEqualTo("Visa (débito)");

    assertThat(PagarmeProvedor.statusDoPedido(json.readTree("{\"status\":\"pending\",\"amount\":100,\"charges\":[]}")).status())
        .isEqualTo(Status.AGUARDANDO);
    assertThat(PagarmeProvedor.statusDoPedido(json.readTree("{\"status\":\"canceled\",\"amount\":100}")).status())
        .isEqualTo(Status.CANCELADA);
    var recusado = PagarmeProvedor.statusDoPedido(json.readTree("""
        {"status":"failed","amount":100,"charges":[{"status":"failed",
         "last_transaction":{"gateway_response_message":"Transação negada"}}]}"""));
    assertThat(recusado.status()).isEqualTo(Status.RECUSADA);
    assertThat(recusado.motivo()).isEqualTo("Transação negada");
    assertThat(PagarmeProvedor.statusDoPedido(json.readTree(
        "{\"status\":\"paid\",\"amount\":100,\"charges\":[{\"status\":\"refunded\",\"amount\":100}]}")).status())
        .isEqualTo(Status.ESTORNADA);
  }

  @Test
  void statusDoLink() throws Exception {
    assertThat(PagarmeProvedor.statusDoLink(json.readTree("{\"status\":\"active\",\"total_paid_sessions\":1}")).status())
        .isEqualTo(Status.PAGA);
    assertThat(PagarmeProvedor.statusDoLink(json.readTree("{\"status\":\"active\",\"total_paid_sessions\":0}")).status())
        .isEqualTo(Status.AGUARDANDO);
    assertThat(PagarmeProvedor.statusDoLink(json.readTree("{\"status\":\"canceled\"}")).status()).isEqualTo(Status.CANCELADA);
  }

  @Test
  void maquininhaSemIdDeParceiroOuSemSerialNaoCobra() {
    assertThatThrownBy(() -> pagarme.criarCobranca(conta(null), pedido("CREDITO", null, "6C582505")))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("id de parceiro");
    assertThatThrownBy(() -> pagarme.criarCobranca(conta("parceiro"), pedido("DEBITO", null, null)))
        .isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
  }

  @Test
  void avisoDeOutroTemaNaoApontaParaCobranca() {
    var aviso = pagarme.lerAviso(conta(null), Map.of(), "{\"id\":\"hook_1\",\"type\":\"recipient.updated\",\"data\":{\"id\":\"re_1\"}}");
    assertThat(aviso.idNaOperadora()).isNull();
    assertThat(aviso.idDoEvento()).isEqualTo("hook_1");
    assertThatThrownBy(() -> pagarme.lerAviso(conta(null), Map.of(), "nao e json"))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
  }
}
