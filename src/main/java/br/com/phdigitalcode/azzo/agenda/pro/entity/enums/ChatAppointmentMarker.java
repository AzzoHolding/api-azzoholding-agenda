package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;

/** Espelha {@code modules/chat/domain/enums/ChatAppointmentMarker.java}. */
public enum ChatAppointmentMarker {
  NAO_INICIADO,
  EM_ANDAMENTO,
  PAUSADO,
  CONCLUIDO,
  NAO_COMPARECEU,
  CANCELADO;

  public static ChatAppointmentMarker from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Marcador de agendamento obrigatorio.");
    }
    try {
      return ChatAppointmentMarker.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Marcador de agendamento invalido: " + value);
    }
  }
}
