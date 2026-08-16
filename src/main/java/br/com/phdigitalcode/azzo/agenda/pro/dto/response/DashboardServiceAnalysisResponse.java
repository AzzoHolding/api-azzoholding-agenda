package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/reports/api/dto/DashboardServiceAnalysisResponse.java}. */
public class DashboardServiceAnalysisResponse {
  public String startDate;
  public String endDate;
  public String professionalId;
  public List<ServiceMetric> services = new ArrayList<>();
  public ServiceMetric mostRequestedService;
  public ServiceMetric leastRequestedService;
  public ServiceMetric mostCancelledService;
  public ServiceMetric mostCompletedService;

  public static class ServiceMetric {
    public String serviceId;
    public String serviceName;
    public int totalAppointments;
    public int completedAppointments;
    public int canceledAppointments;
    public BigDecimal revenueTotal;
    public double completionRate;
    public double cancellationRate;
  }
}
