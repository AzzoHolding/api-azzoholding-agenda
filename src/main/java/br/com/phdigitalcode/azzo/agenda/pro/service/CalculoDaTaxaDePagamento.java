package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TaxaDePagamento;

/**
 * Estima a taxa da operadora, o liquido e o dia em que o dinheiro cai (Fase 5 dos pagamentos).
 *
 * <p>Puro, para testar sem banco. Vale a faixa do mesmo meio que contem o numero de parcelas; sem
 * faixa, nao ha estimativa (o Azzo nao inventa taxa). Taxa = percentual do valor + valor fixo,
 * arredondada ao centavo e nunca maior que o valor. Previsao = dia do pagamento + dias da faixa
 * (dias corridos; cada operadora tem seu calendario, e o dono ajusta os dias da faixa).
 */
public final class CalculoDaTaxaDePagamento {

  private CalculoDaTaxaDePagamento() {}

  public record Estimativa(BigDecimal taxa, BigDecimal liquido, LocalDate previsao) {}

  public static Optional<Estimativa> estimar(
      List<TaxaDePagamento> faixas, String meio, int parcelas, BigDecimal valor, LocalDate diaDoPagamento) {
    if (faixas == null || meio == null || valor == null || diaDoPagamento == null) return Optional.empty();
    int n = Math.max(1, parcelas);
    return faixas.stream()
        .filter(f -> meio.equals(f.getMeio()) && n >= f.getParcelasDe() && n <= f.getParcelasAte())
        .findFirst()
        .map(f -> {
          BigDecimal taxa = valor.multiply(f.getPercentual()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
              .add(f.getValorFixo() == null ? BigDecimal.ZERO : f.getValorFixo())
              .setScale(2, RoundingMode.HALF_UP)
              .min(valor);
          return new Estimativa(taxa, valor.subtract(taxa).setScale(2, RoundingMode.HALF_UP),
              diaDoPagamento.plusDays(f.getDiasParaReceber()));
        });
  }

  /** Faixas do mesmo meio nao podem se sobrepor: uma parcela teria duas taxas. */
  public static Optional<String> conflito(List<TaxaDePagamento> faixas) {
    for (int i = 0; i < faixas.size(); i++) {
      for (int j = i + 1; j < faixas.size(); j++) {
        TaxaDePagamento a = faixas.get(i);
        TaxaDePagamento b = faixas.get(j);
        if (a.getMeio().equals(b.getMeio()) && a.getParcelasDe() <= b.getParcelasAte() && b.getParcelasDe() <= a.getParcelasAte()) {
          return Optional.of("Duas faixas de " + a.getMeio() + " cobrem as mesmas parcelas (" + Math.max(a.getParcelasDe(), b.getParcelasDe())
              + "x). Ajuste as faixas.");
        }
      }
    }
    return Optional.empty();
  }
}
