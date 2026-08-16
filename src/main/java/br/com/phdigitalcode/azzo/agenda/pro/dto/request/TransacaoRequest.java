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

  @NotBlank(message = "type e obrigatorio")
  public String type; // INCOME | EXPENSE

  @NotBlank(message = "category e obrigatoria")
  public String category;

  @NotBlank(message = "description e obrigatoria")
  @Size(max = 500, message = "description deve ter no maximo 500 caracteres")
  public String description;

  @Positive(message = "amount deve ser maior que zero")
  public BigDecimal amount;

  @NotBlank(message = "paymentMethod e obrigatorio")
  public String paymentMethod; // CASH|CREDIT_CARD|DEBIT_CARD|PIX|OTHER

  @NotBlank(message = "date e obrigatoria")
  public String date; // ISO instant ou yyyy-MM-dd

  @AssertTrue(message = "Informe amount em reais")
  public boolean isAmountProvided() {
    return amount != null;
  }
}
