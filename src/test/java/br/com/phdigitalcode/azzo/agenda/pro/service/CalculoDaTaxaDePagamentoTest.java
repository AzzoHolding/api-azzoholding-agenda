package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TaxaDePagamento;

class CalculoDaTaxaDePagamentoTest {

  private static TaxaDePagamento faixa(String meio, int de, int ate, String percentual, String fixo, int dias) {
    TaxaDePagamento t = new TaxaDePagamento();
    t.setMeio(meio);
    t.setParcelasDe(de);
    t.setParcelasAte(ate);
    t.setPercentual(new BigDecimal(percentual));
    t.setValorFixo(new BigDecimal(fixo));
    t.setDiasParaReceber(dias);
    return t;
  }

  private final List<TaxaDePagamento> tabela = List.of(
      faixa("PIX", 1, 1, "0.99", "0", 0),
      faixa("DEBITO", 1, 1, "1.99", "0", 1),
      faixa("CREDITO", 1, 1, "3.49", "0", 30),
      faixa("CREDITO", 2, 6, "4.99", "0.50", 30),
      faixa("CREDITO", 7, 12, "6.50", "0", 30));

  private final LocalDate dia = LocalDate.of(2026, 11, 14);

  @Test
  void escolheAFaixaDoMeioEDasParcelas() {
    var avista = CalculoDaTaxaDePagamento.estimar(tabela, "CREDITO", 1, new BigDecimal("100.00"), dia).orElseThrow();
    assertThat(avista.taxa()).isEqualByComparingTo("3.49");
    assertThat(avista.liquido()).isEqualByComparingTo("96.51");
    assertThat(avista.previsao()).isEqualTo(dia.plusDays(30));

    var tres = CalculoDaTaxaDePagamento.estimar(tabela, "CREDITO", 3, new BigDecimal("80.00"), dia).orElseThrow();
    assertThat(tres.taxa()).isEqualByComparingTo("4.49"); // 3,99 + 0,50
    assertThat(CalculoDaTaxaDePagamento.estimar(tabela, "PIX", 1, new BigDecimal("50.50"), dia).orElseThrow().previsao())
        .isEqualTo(dia);
  }

  @Test
  void semFaixaNaoHaEstimativaETaxaNuncaPassaDoValor() {
    assertThat(CalculoDaTaxaDePagamento.estimar(tabela, "LINK", 1, BigDecimal.TEN, dia)).isEmpty();
    assertThat(CalculoDaTaxaDePagamento.estimar(tabela, "CREDITO", 13, BigDecimal.TEN, dia)).isEmpty();
    var caro = CalculoDaTaxaDePagamento.estimar(List.of(faixa("PIX", 1, 1, "0", "5.00", 0)), "PIX", 1, new BigDecimal("2.00"), dia)
        .orElseThrow();
    assertThat(caro.taxa()).isEqualByComparingTo("2.00");
    assertThat(caro.liquido()).isEqualByComparingTo("0.00");
  }

  @Test
  void faixasSobrepostasSaoConflito() {
    assertThat(CalculoDaTaxaDePagamento.conflito(tabela)).isEmpty();
    assertThat(CalculoDaTaxaDePagamento.conflito(List.of(faixa("CREDITO", 1, 6, "1", "0", 30), faixa("CREDITO", 6, 12, "1", "0", 30))))
        .get().asString().contains("6x");
  }
}
