package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;

/** Espelha {@code modules/scheduling/domain/enums/BookingFunnelStage.java}. */
public enum BookingFunnelStage {
  SERVICE_SELECTION,
  PROFESSIONAL_SELECTION,
  TIME_SELECTION,
  FINAL_REVIEW,
  COMPLETED;

  public static BookingFunnelStage from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Etapa do funil obrigatoria.");
    }
    try {
      return BookingFunnelStage.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Etapa do funil invalida: " + value);
    }
  }
}
