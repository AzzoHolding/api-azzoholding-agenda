package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.time.Instant;

/** O intervalo de uma conciliacao, quantos lancamentos ela concilia e quantos seguem divergentes. */
public class ConciliacaoDoPeriodoResponse {
  public String id;
  public String mode;
  public String period;
  public Instant startsAt;

  /** Fim exclusivo. */
  public Instant endsAt;

  /** So na previa: quantos pendentes a conciliacao vai levar. */
  public int pending;

  public int reconciled;
  public int divergent;
  public Instant createdAt;
  public String createdByName;
}
