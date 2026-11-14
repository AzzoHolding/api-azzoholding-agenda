package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.cielo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class CieloSmartProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final CieloSmartProvedor smart = new CieloSmartProvedor(json);

  /** A transacao de pagamento do exemplo da documentacao (consultar as transacoes de um pedido). */
  private static final String PAGAMENTO = "{\"id\":\"442bbdbe\",\"external_id\":\"0b37f0d8\",\"status\":\"CONFIRMED\","
      + "\"terminal_number\":\"77102648\",\"authorization_code\":\"008619\",\"number\":\"672836\",\"amount\":2000,"
      + "\"transaction_type\":\"PAYMENT\",\"payment_fields\":{\"primary_product_name\":\"CREDITO\","
      + "\"secondary_product_name\":\"A VISTA\",\"number_of_quotas\":0},\"card\":{\"brand\":\"VISA\",\"mask\":\"************3542\"}}";

  private static final String CANCELAMENTO = PAGAMENTO.replace("\"PAYMENT\"", "\"CANCELLATION\"").replace("672836", "672837");

  private JsonNode no(String s) throws Exception {
    return json.readTree(s);
  }

  private static ProvedorDePagamento.PedidoDeCobranca cartao(String meio, int parcelas, String terminal) {
    return new ProvedorDePagamento.PedidoDeCobranca("0f3a1c2b-4d5e-6f70-8192-a3b4c5d6e7f8", meio, new BigDecimal("150.5"),
        parcelas, "Comanda 12 - Corte", null, terminal, null);
  }

  @Test
  void pedidoEmCentavosComNumeroLogicoEParcelas() {
    Map<String, Object> corpo = CieloSmartProvedor.pedidoDaComanda(cartao("CREDITO", 3, " 1234567-8 "));
    assertThat(corpo).containsEntry("number", "0f3a1c2b-4d5e-6f70-8192-a3b4c5d6e7f8").containsEntry("status", "DRAFT")
        .containsEntry("price", 15050L).containsEntry("installments", 3).containsEntry("terminal_number", "1234567-8")
        .containsEntry("reference", "Azzo 0f3a1c2b");
    @SuppressWarnings("unchecked")
    Map<String, Object> item = ((List<Map<String, Object>>) corpo.get("items")).get(0);
    assertThat(item).containsEntry("unit_price", 15050L).containsEntry("quantity", 1).containsEntry("unit_of_measure", "EACH")
        .containsEntry("sku", "AZZO-COMANDA");
    // Debito e credito a vista: 0 parcelas; sem maquininha escolhida, sem terminal_number.
    assertThat(CieloSmartProvedor.pedidoDaComanda(cartao("DEBITO", 3, null))).containsEntry("installments", 0)
        .doesNotContainKey("terminal_number");
  }

  @Test
  void pagamentoConfirmadoViraPagaComNsuAutorizacaoEBandeira() throws Exception {
    var s = CieloSmartProvedor.status(no("{\"status\":\"PAID\",\"price\":2000}"), no("[" + PAGAMENTO + "]"));
    assertThat(s.status()).isEqualTo(Status.PAGA);
    assertThat(s.valorPago()).isEqualByComparingTo("20.00");
    assertThat(s.nsu()).isEqualTo("672836");
    assertThat(s.autorizacao()).isEqualTo("008619");
    assertThat(s.bandeira()).isEqualTo("VISA");
    assertThat(s.cartaoFinal4()).endsWith("3542");
  }

  @Test
  void pagoMesmoSemOPedidoMudarDeStatusQuandoOValorFecha() throws Exception {
    assertThat(CieloSmartProvedor.status(no("{\"status\":\"ENTERED\",\"price\":2000}"), no("[" + PAGAMENTO + "]")).status())
        .isEqualTo(Status.PAGA);
  }

  @Test
  void cancelamentoNaMaquininhaViraEstornada() throws Exception {
    var s = CieloSmartProvedor.status(no("{\"status\":\"PAID\",\"price\":2000}"), no("[" + CANCELAMENTO + "," + PAGAMENTO + "]"));
    assertThat(s.status()).isEqualTo(Status.ESTORNADA);
    assertThat(s.valorEstornado()).isEqualByComparingTo("20.00");
  }

  @Test
  void esperandoFechadoSemPagamentoETransacaoPendente() throws Exception {
    assertThat(CieloSmartProvedor.status(no("{\"status\":\"ENTERED\",\"price\":2000,\"transactions\":[]}"), null).status())
        .isEqualTo(Status.AGUARDANDO);
    assertThat(CieloSmartProvedor.status(no("{\"status\":\"CLOSED\",\"price\":2000}"), no("[]")).status())
        .isEqualTo(Status.CANCELADA);
    String pendente = PAGAMENTO.replace("CONFIRMED", "PENDING");
    assertThat(CieloSmartProvedor.status(no("{\"status\":\"ENTERED\",\"price\":2000}"), no("[" + pendente + "]")).status())
        .isEqualTo(Status.AGUARDANDO);
    // Sem a lista, vale a que vem dentro do pedido.
    assertThat(CieloSmartProvedor.status(no("{\"status\":\"PAID\",\"price\":2000,\"transactions\":[" + PAGAMENTO + "]}"), null)
        .status()).isEqualTo(Status.PAGA);
  }

  @Test
  void soCartaoNaMaquininhaENumeroLogicoValido() {
    var conta = new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX",
        Map.of("clientId", "c", "accessToken", "a", "merchantId", "m"), "https://x");
    assertThatThrownBy(() -> smart.criarCobranca(conta, cartao("PIX", 1, "1234567")))
        .isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
    smart.prepararTerminal(conta, "1234567-8");
    smart.prepararTerminal(conta, "12345678");
    assertThatThrownBy(() -> smart.prepararTerminal(conta, "ABC-123"))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("número lógico");
    assertThat(smart.terminais(conta)).isEmpty();
  }

  @Test
  void avisoSemPedidoNaoConsultaNada() {
    var conta = new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX", Map.of(), "https://x");
    assertThat(smart.lerAviso(conta, Map.of(), "{\"evento\":\"x\"}").idNaOperadora()).isNull();
    assertThatThrownBy(() -> smart.lerAviso(conta, Map.of(), "nao e json"))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
  }
}
