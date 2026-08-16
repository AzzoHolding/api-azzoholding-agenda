package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Espelha {@code modules/settings/application/dto/SpecialClosureDto.java}.
 *
 * <p>LGPD: o campo {@code reason} NUNCA deve aparecer em logs.
 */
public class SpecialClosureDto {

  public UUID id;

  public LocalDate closureDate;

  /** Tipo do fechamento: HOLIDAY, VACATION, RECESS, INTERNAL_EVENT, MANUAL. */
  public String closureType;

  /** true = fechamento de dia inteiro; false = fechamento parcial (usar startTime/endTime). */
  public boolean allDay = true;

  /** Hora de inicio do fechamento parcial. Ignorado se allDay = true. */
  public LocalTime startTime;

  /** Hora de fim do fechamento parcial. Ignorado se allDay = true. */
  public LocalTime endTime;

  /** Motivo do fechamento. LGPD: NUNCA logar este campo. Usar apenas id e closureDate em logs. */
  public String reason;

  /** Profissional impactado. null = fechamento do salao inteiro. */
  public UUID professionalId;

  /** Nome do profissional para exibicao. Preenchido apenas na leitura. */
  public String professionalName;
}
