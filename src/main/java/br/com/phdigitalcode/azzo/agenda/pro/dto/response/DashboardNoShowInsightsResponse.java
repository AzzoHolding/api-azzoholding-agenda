package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/reports/api/dto/DashboardNoShowInsightsResponse.java}. */
public class DashboardNoShowInsightsResponse {
  public String startDate;
  public String endDate;
  public String lastUpdatedAt;
  public int totalNoShows;
  public Integer previousPeriodNoShows;
  public double noShowRate;
  public int lastSevenDaysNoShows;
  public BigDecimal revenueAtRisk;
  public List<Item> recentItems = new ArrayList<>();

  public static class Item {
    public String appointmentId;
    public String clientId;
    public String clientName;
    public String professionalId;
    public String professionalName;
    public String date;
    public String startTime;
    public String endTime;
    public BigDecimal totalPrice;
    public String status;
    public List<String> serviceNames = new ArrayList<>();
  }
}
