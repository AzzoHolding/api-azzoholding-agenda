package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/reports/api/dto/DashboardWhatsAppReactivationResponse.java}. */
public class DashboardWhatsAppReactivationResponse {
  public String startDate;
  public String endDate;
  public int totalAbandoned;
  public int totalReactivated;
  public int totalConverted;
  public double reactivationRate;
  public int stoppedAtServiceSelection;
  public int stoppedAtProfessionalSelection;
  public int stoppedAtTimeSelection;
  public int stoppedAtFinalReview;
  public List<Point> points = new ArrayList<>();

  public static class Point {
    public String metricDate;
    public int abandonedCount;
    public int reactivatedCount;
    public int convertedCount;
  }
}
