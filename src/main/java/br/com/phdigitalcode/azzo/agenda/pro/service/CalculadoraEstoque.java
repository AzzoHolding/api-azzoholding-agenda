package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;

/**
 * Porte verbatim de {@code modules/inventory/application/CalculadoraEstoque.java}, com a mesma
 * visibilidade de pacote do original.
 *
 * <p>Note que {@code AJUSTE} cai no mesmo ramo de {@code SAIDA} (subtracao) — nao e um esquecimento
 * do porte, e o comportamento do original.
 */
final class CalculadoraEstoque {

  private CalculadoraEstoque() {}

  static BigDecimal calcularSaldoPosterior(
      TipoMovimentacaoEstoque tipo, BigDecimal saldoAnterior, BigDecimal quantidade) {
    BigDecimal saldo = nvl(saldoAnterior);
    BigDecimal qtd = nvl(quantidade);
    if (tipo == TipoMovimentacaoEstoque.ENTRADA) {
      return saldo.add(qtd);
    }
    return saldo.subtract(qtd);
  }

  static BigDecimal calcularValorTotalMovimentacao(BigDecimal valorUnitario, BigDecimal quantidade) {
    if (valorUnitario == null) return null;
    return valorUnitario.multiply(nvl(quantidade)).setScale(4, RoundingMode.HALF_UP);
  }

  private static BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
