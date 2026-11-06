package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /finance/transactions/reconcile-period}. */
public class ConciliacaoDoPeriodoRequest {

  /** {@code yyyy-MM-dd} no modo por dia; {@code yyyy-MM} no modo por mes. */
  @NotBlank(message = "Informe o período da conciliação")
  public String period;
}
