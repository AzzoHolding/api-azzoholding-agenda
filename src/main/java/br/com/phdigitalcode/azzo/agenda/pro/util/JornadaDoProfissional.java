package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ProfissionalWorkingHour;

/**
 * O profissional trabalha neste horario?
 *
 * <p>Uma regra so, usada pela agenda interna e pelo agendamento publico. Ate 2026-09-17 ela existia
 * so dentro de {@code ServicoAgendamentos}: o link publico nao conferia a jornada e aceitava marcar
 * no dia de folga do profissional.
 *
 * <p>Regras, iguais as da agenda interna:
 *
 * <ul>
 *   <li>sem jornada configurada: sem restricao;
 *   <li>dia configurado: o atendimento inteiro precisa caber numa janela de trabalho;
 *   <li>dia nao configurado (e ha outros dias configurados): sem restricao para esse dia;
 *   <li>{@code dayOfWeek} pode vir ISO (1=segunda ... 7=domingo) ou JS (0=domingo).
 * </ul>
 */
public final class JornadaDoProfissional {

  private JornadaDoProfissional() {}

  public static boolean atende(
      List<ProfissionalWorkingHour> jornada, LocalDate data, LocalTime inicio, LocalTime fim) {
    if (jornada == null || jornada.isEmpty()) return true;

    int diaIso = data.getDayOfWeek().getValue();
    boolean diaConfigurado = false;

    for (ProfissionalWorkingHour janela : jornada) {
      if (janela == null) continue;
      boolean mesmoDia =
          janela.getDayOfWeek() == diaIso || (diaIso == 7 && janela.getDayOfWeek() == 0);
      if (!mesmoDia) continue;

      diaConfigurado = true;
      if (!janela.isWorking()) continue;
      if (janela.getStartTime() == null
          || janela.getEndTime() == null
          || !janela.getStartTime().isBefore(janela.getEndTime())) {
        continue;
      }
      if (!inicio.isBefore(janela.getStartTime()) && !fim.isAfter(janela.getEndTime())) {
        return true;
      }
    }
    return !diaConfigurado;
  }
}
