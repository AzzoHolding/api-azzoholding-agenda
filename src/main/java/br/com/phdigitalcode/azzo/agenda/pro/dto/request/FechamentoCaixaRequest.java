package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/** Porte verbatim de {@code modules/finance/api/dto/FechamentoCaixaRequest.java}. */
public class FechamentoCaixaRequest {
  @NotNull public Map<String, BigDecimal> countedTotals;
  public String notes;
}
