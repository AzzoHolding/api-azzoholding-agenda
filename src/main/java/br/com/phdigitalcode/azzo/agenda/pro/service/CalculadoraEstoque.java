package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoMovimentacaoEstoque;

/**
 * Porte de {@code modules/inventory/application/CalculadoraEstoque.java}, com a mesma visibilidade
 * de pacote do original.
 *
 * <p><b>Divergencia deliberada do original: {@code AJUSTE} e o saldo FINAL.</b> No original o ajuste
 * caia no ramo da {@code SAIDA} e subtraia — mas a tela chama o tipo de "correcao de contagem" e
 * pede o que ha na prateleira. Quem contava 450,5 ml e digitava 450,5 via o saldo virar 49,5. Agora
 * o numero do ajuste e o saldo que fica; a diferenca (para cima ou para baixo) sai de
 * {@code saldoPosterior - saldoAnterior}.
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
    if (tipo == TipoMovimentacaoEstoque.AJUSTE) {
      return qtd;
    }
    return saldo.subtract(qtd);
  }

  /**
   * Custo medio ponderado depois de uma entrada com preco.
   *
   * <p>O original sobrescrevia o custo com o ultimo preco pago: uma compra em promocao derrubava o
   * valor de todo o estoque. Saldo anterior negativo ou zero nao tem valor a ponderar — ai o custo
   * e o da propria compra.
   */
  static BigDecimal calcularCustoMedio(
      BigDecimal saldoAnterior,
      BigDecimal custoAnterior,
      BigDecimal quantidadeEntrada,
      BigDecimal valorUnitario) {
    BigDecimal saldo = nvl(saldoAnterior);
    BigDecimal qtd = nvl(quantidadeEntrada);
    if (valorUnitario == null) return custoAnterior;
    if (saldo.signum() <= 0 || custoAnterior == null) return valorUnitario;
    BigDecimal total = saldo.add(qtd);
    if (total.signum() <= 0) return valorUnitario;
    return saldo
        .multiply(custoAnterior)
        .add(qtd.multiply(valorUnitario))
        .divide(total, 4, RoundingMode.HALF_UP);
  }

  static BigDecimal calcularValorTotalMovimentacao(BigDecimal valorUnitario, BigDecimal quantidade) {
    if (valorUnitario == null) return null;
    return valorUnitario.multiply(nvl(quantidade)).setScale(4, RoundingMode.HALF_UP);
  }

  private static BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
