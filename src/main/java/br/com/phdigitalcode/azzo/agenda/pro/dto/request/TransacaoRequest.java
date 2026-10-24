package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Porte verbatim de {@code modules/finance/api/dto/TransacaoRequest.java}. */
public class TransacaoRequest {
  public String appointmentId;
  public String professionalId;
  public String productId;
  public String productCategory;

  @NotBlank(message = "type é obrigatório")
  public String type; // INCOME | EXPENSE

  @NotBlank(message = "category é obrigatória")
  public String category;

  @NotBlank(message = "description é obrigatória")
  @Size(max = 500, message = "description deve ter no máximo 500 caracteres")
  public String description;

  @Positive(message = "amount deve ser maior que zero")
  public BigDecimal amount;

  @NotBlank(message = "paymentMethod é obrigatório")
  public String paymentMethod; // CASH|CREDIT_CARD|DEBIT_CARD|PIX|OTHER

  @NotBlank(message = "date é obrigatória")
  public String date; // ISO instant ou yyyy-MM-dd

  @AssertTrue(message = "Informe amount em reais")
  public boolean isAmountProvided() {
    return amount != null;
  }
}
