package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

/** Quantos lancamentos o periodo conciliou, e quantos seguem divergentes. */
public class ConciliacaoDoPeriodoResponse {
  public String period;
  public int reconciled;
  public int divergent;
}
