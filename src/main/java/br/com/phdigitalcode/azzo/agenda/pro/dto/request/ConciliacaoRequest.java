package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /finance/transactions/{id}/reconcile} (28/10/2026).
 *
 * <p>O pedido diz o estado que quer, e nao "inverta": o PATCH sem corpo invertia, e um duplo clique
 * ou uma nova tentativa depois de falha de rede desconciliava sem ninguem perceber. Repetir o mesmo
 * pedido nao muda nada.
 */
public class ConciliacaoRequest {

  /** {@code true} concilia, {@code false} reabre. Obrigatorio quando ha corpo. */
  public Boolean reconciled;

  /** Por que reabriu. Obrigatoria para reabrir; vai para a auditoria. */
  @Size(max = 500, message = "Justificativa deve ter no máximo 500 caracteres")
  public String reason;
}
