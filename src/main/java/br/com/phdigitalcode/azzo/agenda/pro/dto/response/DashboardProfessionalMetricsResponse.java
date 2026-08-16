package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Espelha {@code modules/reports/api/dto/DashboardProfessionalMetricsResponse.java}. */
public class DashboardProfessionalMetricsResponse {
  public String startDate;
  public String endDate;
  public String professionalId;
  public BigDecimal revenueTotal;
  public BigDecimal commissionTotal;
  public int completedServices;
  public int clientsServed;
}
