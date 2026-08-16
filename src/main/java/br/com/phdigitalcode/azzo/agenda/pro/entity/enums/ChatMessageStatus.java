package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;

/** Espelha {@code modules/chat/domain/enums/ChatMessageStatus.java}. */
public enum ChatMessageStatus {
  QUEUED,
  SENT,
  DELIVERED,
  READ,
  FAILED;

  public static ChatMessageStatus from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Status da mensagem obrigatorio.");
    }
    try {
      return ChatMessageStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Status da mensagem invalido: " + value);
    }
  }
}
