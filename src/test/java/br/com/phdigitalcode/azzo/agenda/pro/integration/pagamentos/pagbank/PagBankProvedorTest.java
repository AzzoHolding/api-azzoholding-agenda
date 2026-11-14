package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.pagbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class PagBankProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final PagBankProvedor pagbank = new PagBankProvedor(json);

  private static ProvedorDePagamento.PedidoDeCobranca pix(ProvedorDePagamento.Pagador pagador) {
    return new ProvedorDePagamento.PedidoDeCobranca("11111111-2222-3333-4444-555555555555", "PIX",
        new BigDecimal("50.5"), 1, "Comanda 12", pagador, null, Instant.now().plusSeconds(1800));
  }

  @Test
  void pedidoPixComQrCodeEAvisoSoEmHttps() {
    var pagador = new ProvedorDePagamento.Pagador(null, "Ana Souza", null, null, "123.456.789-09");
    var corpo = PagBankProvedor.pedidoPix(pix(pagador), "https://azzo/webhook/pagamentos/pagbank/tok");
    assertThat(corpo).containsEntry("reference_id", "11111111-2222-3333-4444-555555555555");
    assertThat(corpo.toString()).contains("tax_id=12345678909").contains("value=5050").contains("expiration_date=")
        .contains("notification_urls=[https://azzo/webhook");
    assertThat(PagBankProvedor.pedidoPix(pix(pagador), "http://localhost/x")).doesNotContainKey("notification_urls");
    assertThatThrownBy(() -> PagBankProvedor.pedidoPix(pix(null), null))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("CPF");
  }

  @Test
  void statusPelosCharges() throws Exception {
    String qr = "\"qr_codes\":[{\"amount\":{\"value\":5050}}]";
    assertThat(PagBankProvedor.status(json.readTree("{" + qr + "}")).status()).isEqualTo(Status.AGUARDANDO);
    var pago = PagBankProvedor.status(json.readTree("{" + qr + ",\"charges\":[{\"status\":\"PAID\","
        + "\"amount\":{\"value\":5050,\"summary\":{\"total\":5050,\"paid\":5050,\"refunded\":0}},"
        + "\"payment_response\":{\"reference\":\"0324\"}}]}"));
    assertThat(pago.status()).isEqualTo(Status.PAGA);
    assertThat(pago.valorPago()).isEqualByComparingTo("50.50");
    assertThat(pago.nsu()).isEqualTo("0324");
    assertThat(PagBankProvedor.status(json.readTree("{" + qr + ",\"charges\":[{\"status\":\"CANCELED\","
        + "\"amount\":{\"value\":5050,\"summary\":{\"paid\":5050,\"refunded\":5050}}}]}")).status()).isEqualTo(Status.ESTORNADA);
    assertThat(PagBankProvedor.status(json.readTree("{" + qr + ",\"charges\":[{\"status\":\"PAID\","
        + "\"amount\":{\"value\":5050,\"summary\":{\"paid\":5050,\"refunded\":1000}}}]}")).status()).isEqualTo(Status.ESTORNO_PARCIAL);
  }

  @Test
  void avisoComAssinaturaErradaERecusado() {
    var conta = new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX", Map.of("token", "tok"), "https://x");
    String corpo = "{\"id\":\"recipient_1\"}";
    assertThatThrownBy(() -> pagbank.lerAviso(conta, Map.of("x-authenticity-token", "0".repeat(64)), corpo))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    // Assinatura certa e corpo que nao e pedido: aceito e ignorado (sem consultar a API).
    var aviso = pagbank.lerAviso(conta, Map.of("x-authenticity-token", PagBankProvedor.sha256("tok-" + corpo)), corpo);
    assertThat(aviso.idNaOperadora()).isNull();
  }
}
