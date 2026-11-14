package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** As contas do resgate (relatorio de 14/11: Alto 2, Baixo 2, Medio 5). */
class ServicoDePontosTest {

  private static final BigDecimal MIL = new BigDecimal("1000");

  @Test
  void valorSempreArredondadoParaBaixo() {
    // 1.000 pontos = R$ 1,00: 4 pontos nao valem um centavo; 5 tambem nao (antes davam R$ 0,01).
    assertThat(ServicoDePontos.valorEmReais(4, MIL)).isEqualByComparingTo("0.00");
    assertThat(ServicoDePontos.valorEmReais(5, MIL)).isEqualByComparingTo("0.00");
    assertThat(ServicoDePontos.valorEmReais(10, MIL)).isEqualByComparingTo("0.01");
    assertThat(ServicoDePontos.valorEmReais(1999, MIL)).isEqualByComparingTo("1.99");
    assertThat(ServicoDePontos.valorEmReais(3000, new BigDecimal("100"))).isEqualByComparingTo("30.00");
  }

  @Test
  void resgatarDePoucoEmPoucoNaoRendeMais() {
    // Duas vezes 5 pontos = o mesmo que 10 de uma vez: o valor e sempre do total na comanda.
    BigDecimal primeiro = ServicoDePontos.valorEmReais(5, MIL);
    BigDecimal total = ServicoDePontos.valorEmReais(5 + 5, MIL);
    assertThat(total).isEqualByComparingTo(ServicoDePontos.valorEmReais(10, MIL));
    assertThat(primeiro.add(total.subtract(primeiro))).isEqualByComparingTo("0.01");
  }

  @Test
  void menorEMaiorQuantidadeParaAMensagem() {
    assertThat(ServicoDePontos.menorTotalQueValeMais(BigDecimal.ZERO.setScale(2), MIL)).isEqualTo(10);
    assertThat(ServicoDePontos.menorTotalQueValeMais(new BigDecimal("0.01"), MIL)).isEqualTo(20);
    // Ate R$ 20,00 com 100 pontos por real: 2.000 pontos (2.001 ja valeria R$ 20,01).
    assertThat(ServicoDePontos.maiorTotalAte(new BigDecimal("20.00"), new BigDecimal("100"))).isEqualTo(2000);
    assertThat(ServicoDePontos.maiorTotalAte(new BigDecimal("0.00"), MIL)).isEqualTo(9);
  }

  @Test
  void motivoComUmTextoDeResgateSo() {
    assertThat(ServicoComanda.motivoComResgate("teste", 300)).isEqualTo("teste; Resgate de 300 pontos de fidelidade");
    // Resgatar, desfazer e resgatar de novo: um texto so, com o total (antes ficava repetido).
    String depois = ServicoComanda.motivoComResgate("teste; Resgate de 300 pontos de fidelidade; Resgate de 300 pontos de fidelidade", 300);
    assertThat(depois).isEqualTo("teste; Resgate de 300 pontos de fidelidade");
    assertThat(ServicoComanda.motivoComResgate("teste; Resgate de 300 pontos de fidelidade", 0)).isEqualTo("teste");
    assertThat(ServicoComanda.motivoComResgate("Resgate de 300 pontos de fidelidade", 0)).isNull();
    assertThat(ServicoComanda.motivoComResgate(null, 50)).isEqualTo("Resgate de 50 pontos de fidelidade");
  }

  @Test
  void pontoFracionadoERecusado() {
    assertThat(ServicoComanda.pontosInteiros(new BigDecimal("40"))).isEqualTo(40);
    assertThat(ServicoComanda.pontosInteiros(new BigDecimal("40.0"))).isEqualTo(40);
    assertThatThrownBy(() -> ServicoComanda.pontosInteiros(new BigDecimal("1.5"))).hasMessageContaining("inteiros");
    assertThatThrownBy(() -> ServicoComanda.pontosInteiros(BigDecimal.ZERO)).hasMessageContaining("Informe");
    assertThatThrownBy(() -> ServicoComanda.pontosInteiros(new BigDecimal("-3"))).hasMessageContaining("Informe");
    assertThatThrownBy(() -> ServicoComanda.pontosInteiros(null)).hasMessageContaining("Informe");
  }
}
