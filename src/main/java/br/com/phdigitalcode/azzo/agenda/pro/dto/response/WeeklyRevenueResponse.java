package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Espelha {@code modules/reports/api/dto/WeeklyRevenueResponse.java}. */
public class WeeklyRevenueResponse {
  public List<Point> points = new ArrayList<>();
  public BigDecimal total = BigDecimal.ZERO;
  public double average;

  public static class Point {
    public String day;
    public String date;
    public BigDecimal value;
  }
}
