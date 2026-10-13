package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;

/**
 * Indicadores da ficha do cliente (D6/R1 do plano de design): frequencia, retorno previsto,
 * comparecimento e gasto recente. Tudo derivado de visitas e agendamentos que ja existem.
 *
 * <p>Campo {@code null} quer dizer "sem base para calcular" (ex.: uma visita so nao tem
 * frequencia), e nao zero.
 */
public class ClienteIndicadoresResponse {
  /** Inicio da janela de comparecimento e gasto recente (12 meses ate hoje). */
  public String since;
  /** Media de dias entre as ultimas visitas (ate 6 intervalos); null com menos de 2 dias de visita. */
  public Integer averageIntervalDays;
  /** Ultima visita + intervalo medio; null sem intervalo. */
  public String expectedReturn;
  /** Agendamentos concluidos na janela. */
  public int attended;
  /** Agendamentos marcados como "Nao compareceu" na janela. */
  public int noShows;
  /** Agendamentos cancelados na janela. */
  public int cancelled;
  /** attended / (attended + noShows), de 0 a 100; null sem nenhum dos dois. */
  public Integer attendanceRate;
  /** O que o cliente pagou na janela, pela mesma regra do total gasto. */
  public BigDecimal spentLast12Months;
}
