package br.com.phdigitalcode.azzo.agenda.pro.dto.assistant;

import java.util.Locale;

/**
 * Espelha {@code application/dto/assistant/AssistantStage.java} do Quarkus original. Porte
 * verbatim (framework-agnostico).
 */
public enum AssistantStage {
  START,
  ASK_CANCEL_APPOINTMENT,
  ASK_RESCHEDULE_APPOINTMENT,
  ASK_NAME,
  ASK_SERVICE,
  ASK_PROFESSIONAL,
  ASK_DATE,
  ASK_PERIOD,
  ASK_TIME,
  CONFIRMATION,
  COMPLETED,
  AWAITING_APPOINTMENT_CONFIRMATION,
  AWAITING_REACTIVATION_REPLY;

  public static AssistantStage from(String value) {
    if (value == null || value.isBlank()) return null;
    return AssistantStage.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
