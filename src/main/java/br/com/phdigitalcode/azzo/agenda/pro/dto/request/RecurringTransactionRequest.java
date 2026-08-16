package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Porte verbatim de {@code modules/finance/api/dto/RecurringTransactionRequest.java}. */
public class RecurringTransactionRequest {

  @NotBlank(message = "Tipo e obrigatorio")
  public String type; // INCOME | EXPENSE

  public String categoryId; // UUID opcional

  @NotBlank(message = "Descricao e obrigatoria")
  @Size(max = 500)
  public String description;

  @NotNull
  @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
  public BigDecimal amount;

  @NotBlank(message = "Forma de pagamento e obrigatoria")
  public String paymentMethod;

  @NotBlank(message = "Frequencia e obrigatoria")
  public String frequency; // MONTHLY | WEEKLY

  /** Dia do mes (1-28) - obrigatorio para MONTHLY */
  public Integer dayOfMonth;

  /** Dia da semana (0=dom ... 6=sab) - obrigatorio para WEEKLY */
  public Integer dayOfWeek;
}
