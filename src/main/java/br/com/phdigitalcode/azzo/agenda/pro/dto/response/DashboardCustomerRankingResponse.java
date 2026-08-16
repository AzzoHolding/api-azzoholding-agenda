package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/reports/api/dto/DashboardCustomerRankingResponse.java}. */
public class DashboardCustomerRankingResponse {
  public String startDate;
  public String endDate;
  public String lastUpdatedAt;
  public List<CustomerMetric> items = new ArrayList<>();

  public static class CustomerMetric {
    public int rank;
    public String clientId;
    public String clientName;
    public int completedAppointments;
    public int completedServices;
    public BigDecimal revenueTotal;
    public String lastAppointmentDate;
  }
}
