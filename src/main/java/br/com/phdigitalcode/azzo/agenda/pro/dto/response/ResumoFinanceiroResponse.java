package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Porte verbatim de {@code modules/finance/api/dto/ResumoFinanceiroResponse.java}. */
public class ResumoFinanceiroResponse {
  public BigDecimal totalIncome;
  public BigDecimal totalExpenses;
  public BigDecimal balance;

  /**
   * Quantos lancamentos estao pendentes e quantos conciliados (29/10/2026). Seguem todos os filtros
   * menos o de conciliacao: sao os numeros das abas, e o filtro da aba zeraria as outras.
   */
  public long pendingCount;
  public long reconciledCount;
}
