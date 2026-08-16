package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/** Porte verbatim de {@code modules/finance/api/dto/RecurringTransactionResponse.java}. */
public class RecurringTransactionResponse {
  public String id;
  public String type;
  public String categoryId;
  public String categoryName;
  public String description;
  public BigDecimal amount;
  public String paymentMethod;
  public String frequency;
  public Integer dayOfMonth;
  public Integer dayOfWeek;
  public boolean active;
  public String createdAt;
}
