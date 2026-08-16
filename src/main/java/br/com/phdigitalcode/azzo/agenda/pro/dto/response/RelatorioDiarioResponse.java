package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Espelha {@code modules/reports/api/dto/RelatorioDiarioResponse.java}. */
public class RelatorioDiarioResponse {
  public String date;
  public int totalAppointments;
  public BigDecimal totalRevenue;
  public BigDecimal totalExpenses;
  public BigDecimal balance;
}
