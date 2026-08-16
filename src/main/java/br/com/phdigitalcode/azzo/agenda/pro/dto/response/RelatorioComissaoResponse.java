package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Espelha {@code modules/reports/api/dto/RelatorioComissaoResponse.java}. */
public class RelatorioComissaoResponse {
  public String professionalId;
  public String from;
  public String to;
  public BigDecimal totalRevenue;
  public BigDecimal commissionRate;
  public BigDecimal commissionValue;
}
