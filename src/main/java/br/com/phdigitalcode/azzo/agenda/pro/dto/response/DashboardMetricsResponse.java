package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Espelha {@code modules/reports/api/dto/DashboardMetricsResponse.java}. */
public class DashboardMetricsResponse {
  public int todayAppointments;
  public BigDecimal todayRevenue;
  public BigDecimal monthlyRevenue;
  public int totalClients;
  public Double todayAppointmentsGrowthPercent;
  public Double todayRevenueGrowthPercent;
  public Double totalClientsGrowthPercent;
  public Double monthlyRevenueGrowthPercent;
  public int pendingAppointments;
  public int completedToday;
  public int notConcludedToday;
  public int stoppedAtServiceSelection;
  public int stoppedAtProfessionalSelection;
  public int stoppedAtTimeSelection;
  public int stoppedAtFinalReview;
  public int whatsAppOpenFlowsToday;
  public int whatsAppStoppedAtServiceSelection;
  public int whatsAppStoppedAtProfessionalSelection;
  public int whatsAppStoppedAtTimeSelection;
  public int whatsAppStoppedAtFinalReview;
}
