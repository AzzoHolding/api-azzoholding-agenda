package br.com.phdigitalcode.azzo.agenda.pro.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Contas dos indicadores da ficha do cliente, separadas do SQL para poderem ser testadas. */
public final class IndicadoresDoCliente {

  /** Quantos intervalos recentes entram na media: o habito de agora pesa mais que o de anos atras. */
  public static final int INTERVALOS_NA_MEDIA = 6;

  private IndicadoresDoCliente() {}

  /**
   * Media de dias entre os ultimos dias de visita. Recebe os dias em qualquer ordem, com ou sem
   * repeticao (duas visitas no mesmo dia contam como um dia).
   */
  public static Integer intervaloMedioEmDias(List<LocalDate> diasDeVisita) {
    if (diasDeVisita == null) return null;
    List<LocalDate> dias = diasDeVisita.stream().filter(Objects::nonNull).distinct().sorted().toList();
    if (dias.size() < 2) return null;
    List<LocalDate> recentes = dias.subList(Math.max(0, dias.size() - INTERVALOS_NA_MEDIA - 1), dias.size());
    long total = ChronoUnit.DAYS.between(recentes.get(0), recentes.get(recentes.size() - 1));
    return (int) Math.round((double) total / (recentes.size() - 1));
  }

  /** Comparecimento em porcentagem inteira; null quando nao ha concluido nem falta. */
  public static Integer taxaDeComparecimento(int concluidos, int faltas) {
    int base = concluidos + faltas;
    if (base <= 0) return null;
    return (int) Math.round(100.0 * concluidos / base);
  }
}
