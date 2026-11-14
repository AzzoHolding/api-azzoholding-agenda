package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.asaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class AsaasProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final AsaasProvedor asaas = new AsaasProvedor(json);
  private final ProvedorDePagamento.Conta conta = new ProvedorDePagamento.Conta(
      "c1", "t1", "SANDBOX", Map.of("apiKey", "k", "webhookToken", "tok"), "https://x/webhook");

  @Test
  void statusDoAsaasViraStatusDoAzzoComTaxaELiquido() throws Exception {
    var pago = AsaasProvedor.status(json.readTree(
        "{\"status\":\"RECEIVED\",\"value\":100.00,\"netValue\":98.01,\"estimatedCreditDate\":\"2026-11-14\"}"));
    assertThat(pago.status()).isEqualTo(Status.PAGA);
    assertThat(pago.taxa()).isEqualByComparingTo("1.99");
    assertThat(pago.valorLiquido()).isEqualByComparingTo("98.01");
    assertThat(pago.previsaoRecebimento()).hasToString("2026-11-14");

    assertThat(AsaasProvedor.status(json.readTree("{\"status\":\"PENDING\"}")).status()).isEqualTo(Status.AGUARDANDO);
    assertThat(AsaasProvedor.status(json.readTree("{\"status\":\"OVERDUE\"}")).status()).isEqualTo(Status.EXPIRADA);
    assertThat(AsaasProvedor.status(json.readTree("{\"status\":\"REFUNDED\"}")).status()).isEqualTo(Status.ESTORNADA);
    assertThat(AsaasProvedor.status(json.readTree("{\"status\":\"PENDING\",\"deleted\":true}")).status())
        .isEqualTo(Status.CANCELADA);
  }

  @Test
  void avisoDoAsaasTraduzidoEConferidoPeloToken() {
    String corpo = "{\"id\":\"evt_1\",\"event\":\"PAYMENT_RECEIVED\",\"payment\":{\"id\":\"pay_9\",\"status\":\"RECEIVED\",\"value\":10,\"netValue\":9.5}}";

    var aviso = asaas.lerAviso(conta, Map.of("asaas-access-token", "tok"), corpo);
    assertThat(aviso.idDoEvento()).isEqualTo("evt_1");
    assertThat(aviso.idNaOperadora()).isEqualTo("pay_9");
    assertThat(aviso.status().status()).isEqualTo(Status.PAGA);

    assertThatThrownBy(() -> asaas.lerAviso(conta, Map.of("asaas-access-token", "outro"), corpo))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    assertThatThrownBy(() -> asaas.lerAviso(conta, Map.of(), "isto nao e json"))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
  }

  @Test
  void asaasSoFazPixELinkECobraComDocumento() {
    assertThat(asaas.capacidades()).contains(ProvedorDePagamento.Capacidade.PIX, ProvedorDePagamento.Capacidade.LINK)
        .doesNotContain(ProvedorDePagamento.Capacidade.MAQUININHA_NUVEM);
    var pedido = new ProvedorDePagamento.PedidoDeCobranca("c", "CREDITO", java.math.BigDecimal.TEN, 1, "x", null, null, null);
    assertThatThrownBy(() -> asaas.criarCobranca(conta, pedido)).isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
    var semDocumento = new ProvedorDePagamento.PedidoDeCobranca("c", "PIX", java.math.BigDecimal.TEN, 1, "x",
        new ProvedorDePagamento.Pagador(null, "Ana", null, null, null), null, null);
    assertThatThrownBy(() -> asaas.criarCobranca(conta, semDocumento))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("CPF ou CNPJ");
  }
}
