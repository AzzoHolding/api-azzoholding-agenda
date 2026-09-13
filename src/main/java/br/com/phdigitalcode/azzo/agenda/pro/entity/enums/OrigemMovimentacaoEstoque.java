package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/**
 * Porte de {@code modules/inventory/domain/enums/OrigemMovimentacaoEstoque.java}.
 *
 * <p>{@code VENDA} nao existe no original: la a venda de produto em comanda era gravada como
 * {@code MANUAL}, indistinguivel de uma baixa por quebra — e o painel contava venda como perda. A
 * coluna e {@code VARCHAR(20)} sem {@code CHECK}, entao o valor novo nao precisa de migration.
 */
public enum OrigemMovimentacaoEstoque {
  MANUAL,
  COMPRA,
  SERVICO,
  INVENTARIO,
  VENDA
}
