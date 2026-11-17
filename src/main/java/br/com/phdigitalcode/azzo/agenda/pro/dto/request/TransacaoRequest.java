package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Porte verbatim de {@code modules/finance/api/dto/TransacaoRequest.java}. */
public class TransacaoRequest {
  public String appointmentId;
  public String professionalId;
  public String productId;
  public String productCategory;

  @NotBlank(message = "Tipo é obrigatório")
  public String type; // INCOME | EXPENSE

  @NotBlank(message = "Categoria é obrigatória")
  public String category;

  @NotBlank(message = "Descrição é obrigatória")
  @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
  public String description;

  @Positive(message = "Valor deve ser maior que zero")
  // R$ 99.999.999.999.999 era aceito (analise de 15/11/2026, M5): um erro de digitacao estragava
  // os totais e os relatorios.
  @DecimalMax(value = "9999999.99", message = "Valor acima do limite de R$ 9.999.999,99. Confira o valor digitado.")
  public BigDecimal amount;

  @NotBlank(message = "Forma de pagamento é obrigatória")
  public String paymentMethod; // CASH|CREDIT_CARD|DEBIT_CARD|PIX|OTHER

  @NotBlank(message = "Data é obrigatória")
  public String date; // ISO instant ou yyyy-MM-dd

  @AssertTrue(message = "Informe o valor em reais")
  public boolean isAmountProvided() {
    return amount != null;
  }
}
