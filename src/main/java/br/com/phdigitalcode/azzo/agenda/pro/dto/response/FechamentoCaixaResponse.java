package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Porte verbatim de {@code modules/finance/api/dto/FechamentoCaixaResponse.java}. */
public class FechamentoCaixaResponse {
  public String id;
  public String tenantId;
  public String businessDate;
  public String status;
  public String openedAt;
  public String openedBy;
  public String openingNotes;
  public String closedAt;
  public String closedBy;
  public String closingNotes;
  public Map<String, BigDecimal> expectedTotals;
  public Map<String, BigDecimal> countedTotals;
  public Map<String, BigDecimal> differenceTotals;
  public BigDecimal totalExpected;
  public BigDecimal totalCounted;
  public BigDecimal totalDifference;
  public List<CommissionSummaryItem> commissionSummary = new ArrayList<>();

  /**
   * As comandas ainda abertas, para o caixa OPEN.
   *
   * <p>Vem na propria resposta do caixa para a tela poder avisar ANTES de alguem clicar em fechar:
   * descobrir no erro ja e tarde quando a pessoa esta de saida.
   */
  public List<ComandaAbertaItem> comandasAbertas = new ArrayList<>();

  public static class ComandaAbertaItem {
    public String id;
    public String clientId;
    public BigDecimal total;
    public String openedAt;
    public int itens;
  }

  public static class CommissionSummaryItem {
    public String professionalId;
    public String professionalName;
    public BigDecimal totalRevenue;
    public BigDecimal commissionAmount;
    public BigDecimal commissionRate;
  }
}
