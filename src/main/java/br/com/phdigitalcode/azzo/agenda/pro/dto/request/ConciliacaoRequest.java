package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /finance/transactions/{id}/reconcile}.
 *
 * <p>O pedido diz o estado que quer, e nao "inverta": o PATCH sem corpo invertia, e um duplo clique
 * ou uma nova tentativa desconciliava sem ninguem perceber (28/10/2026). Repetir o mesmo pedido nao
 * muda nada.
 */
public class ConciliacaoRequest {

  /** {@code PENDING}, {@code RECONCILED} ou {@code DIVERGENT}. */
  public String status;

  /** Forma anterior: {@code true} concilia, {@code false} reabre. Usada se {@code status} faltar. */
  public Boolean reconciled;

  /** Por que reabriu, ou o que divergiu. Obrigatoria nesses dois casos. */
  @Size(max = 500, message = "Justificativa deve ter no máximo 500 caracteres")
  public String reason;

  /** Quanto aparece no extrato, quando diverge. Opcional e informativo: nao entra em saldo nenhum. */
  @PositiveOrZero(message = "Valor do extrato não pode ser negativo")
  public BigDecimal statementAmount;
}
