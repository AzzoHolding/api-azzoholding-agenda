package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;

/** Espelha {@code modules/chat/domain/enums/ChatMessageDirection.java}. */
public enum ChatMessageDirection {
  OUTBOUND,
  INBOUND;

  public static ChatMessageDirection from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Direcao da mensagem obrigatoria.");
    }
    try {
      return ChatMessageDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Direcao da mensagem invalida: " + value);
    }
  }
}
