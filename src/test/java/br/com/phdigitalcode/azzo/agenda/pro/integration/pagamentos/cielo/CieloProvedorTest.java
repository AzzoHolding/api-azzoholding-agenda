package br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.cielo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento;
import br.com.phdigitalcode.azzo.agenda.pro.integration.pagamentos.ProvedorDePagamento.Status;

class CieloProvedorTest {

  private final ObjectMapper json = new ObjectMapper();
  private final CieloProvedor cielo = new CieloProvedor(json);

  private static ProvedorDePagamento.PedidoDeCobranca pix(ProvedorDePagamento.Pagador pagador) {
    return new ProvedorDePagamento.PedidoDeCobranca("11111111-2222-3333-4444-555555555555", "PIX",
        new BigDecimal("50.5"), 1, "Comanda 12", pagador, null, Instant.now().plusSeconds(1800));
  }

  @Test
  void vendaPixComDocumentoEProvedorNovoSoEmProducao() {
    var pagador = new ProvedorDePagamento.Pagador(null, "Ana Souza", null, null, "123.456.789-09");
    var producao = CieloProvedor.vendaPix(pix(pagador), true);
    assertThat(producao).containsEntry("MerchantOrderId", "11111111-2222-3333-4444-555555555555");
    assertThat(producao.toString()).contains("Type=Pix").contains("Provider=Cielo2").contains("Amount=5050")
        .contains("Identity=12345678909").contains("IdentityType=CPF").contains("Expiration=");
    assertThat(CieloProvedor.vendaPix(pix(pagador), false).toString()).doesNotContain("Cielo2");
    assertThatThrownBy(() -> CieloProvedor.vendaPix(pix(null), true))
        .isInstanceOf(ProvedorDePagamento.FalhaNaOperadora.class).hasMessageContaining("CPF");
  }

  @Test
  void statusDaCieloViraStatusDoAzzo() throws Exception {
    var pago = CieloProvedor.status(json.readTree("{\"Payment\":{\"Status\":2,\"Amount\":5050,\"ProofOfSale\":\"123\"}}"));
    assertThat(pago.status()).isEqualTo(Status.PAGA);
    assertThat(pago.valorPago()).isEqualByComparingTo("50.50");
    assertThat(pago.nsu()).isEqualTo("123");
    assertThat(CieloProvedor.status(json.readTree("{\"Payment\":{\"Status\":12}}")).status()).isEqualTo(Status.AGUARDANDO);
    assertThat(CieloProvedor.status(json.readTree("{\"Payment\":{\"Status\":11}}")).status()).isEqualTo(Status.ESTORNADA);
    assertThat(CieloProvedor.status(json.readTree("{\"Payment\":{\"Status\":13}}")).status()).isEqualTo(Status.CANCELADA);
    var negado = CieloProvedor.status(json.readTree("{\"Payment\":{\"Status\":3,\"ReturnMessage\":\"Nao autorizada\"}}"));
    assertThat(negado.status()).isEqualTo(Status.RECUSADA);
    assertThat(negado.motivo()).isEqualTo("Nao autorizada");
  }

  @Test
  void avisoSemOCabecalhoCombinadoERecusado() {
    var conta = new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX",
        Map.of("merchantId", "m", "merchantKey", "k", "segredoDoAviso", "combinado"), "https://x");
    assertThatThrownBy(() -> cielo.lerAviso(conta, Map.of(), "{\"PaymentId\":\"p\",\"ChangeType\":1}"))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    assertThatThrownBy(() -> cielo.lerAviso(conta, Map.of("x-azzo-aviso", "outro"), "{\"PaymentId\":\"p\",\"ChangeType\":1}"))
        .isInstanceOf(ProvedorDePagamento.AvisoInvalido.class);
    // Com o cabecalho certo e sem PaymentId, nao aponta para cobranca (e nao consulta nada).
    assertThat(cielo.lerAviso(conta, Map.of("x-azzo-aviso", "combinado"), "{\"ChangeType\":2}").idNaOperadora()).isNull();
  }

  @Test
  void soPixNestaFase() {
    var conta = new ProvedorDePagamento.Conta("c1", "t1", "SANDBOX", Map.of("merchantId", "m", "merchantKey", "k"), "https://x");
    var cartao = new ProvedorDePagamento.PedidoDeCobranca("c", "CREDITO", BigDecimal.TEN, 1, "x", null, "S1", null);
    assertThatThrownBy(() -> cielo.criarCobranca(conta, cartao)).isInstanceOf(ProvedorDePagamento.NaoSuportado.class);
  }
}
