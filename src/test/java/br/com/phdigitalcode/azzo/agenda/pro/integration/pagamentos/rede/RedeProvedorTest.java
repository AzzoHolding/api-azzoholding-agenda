package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.rede;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class RedeProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final RedeProvedor rede = new RedeProvedor(json);

  @Test
  void transacaoPixSemCaractereEspecialEComExpiracaoLocal() {
    var pedido = new ProvedorDePagamento.PedidoDeCobranca("11111111-2222-3333-4444-555555555555", "PIX",
        new BigDecimal("10"), 1, "Comanda", null, null, Instant.parse("2026-11-14T15:30:00Z"));
    var t = RedeProvedor.transacaoPix(pedido);
    assertThat(t).containsEntry("kind", "Pix").containsEntry("amount", 1000L)
        .containsEntry("reference", "11111111222233334444555555555555");
    // 15:30 UTC = 12:30 em Brasilia, sem fuso no texto (formato da Rede).
    assertThat(t.get("qrCode").toString()).contains("dateTimeExpiration=2026-11-14T12:30:00");
  }

  @Test
  void statusDaRede() throws Exception {
    var pago = RedeProvedor.status(json.readTree("{\"tid\":\"1\",\"amount\":1000,\"authorization\":{\"status\":\"Approved\",\"nsu\":\"77\"}}"));
    assertThat(pago.status()).isEqualTo(Status.PAGA);
    assertThat(pago.valorPago()).isEqualByComparingTo("10.00");
    assertThat(pago.nsu()).isEqualTo("77");
    assertThat(RedeProvedor.status(json.readTree("{\"authorization\":{\"status\":\"Pending\"}}")).status()).isEqualTo(Status.AGUARDANDO);
    assertThat(RedeProvedor.status(json.readTree("{\"authorization\":{\"status\":\"Canceled\"}}")).status()).isEqualTo(Status.ESTORNADA);
    assertThat(RedeProvedor.status(json.readTree("{\"returnCode\":\"3036\"}")).status()).isEqualTo(Status.EXPIRADA);
  }

  @Test
  void avisoSemOCabecalhoCombinadoERecusado() {
    var conta = new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX",
        Map.of("pv", "1", "chaveDeIntegracao", "k", "segredoDoAviso", "Bearer combinado"), "https://x");
    String corpo = "{\"id\":\"a1\",\"events\":[\"PV.UPDATE_TRANSACTION_PIX\"],\"data\":{\"id\":\"404\"}}";
    assertThatThrownBy(() -> rede.lerAviso(conta, Map.of(), corpo)).isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    assertThatThrownBy(() -> rede.lerAviso(conta, Map.of("authorization", "Bearer outro"), corpo))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    var semTid = rede.lerAviso(conta, Map.of("authorization", "Bearer combinado"), "{\"id\":\"a2\",\"events\":[\"X\"],\"data\":{}}");
    assertThat(semTid.idNaOperadora()).isNull();
  }
}
