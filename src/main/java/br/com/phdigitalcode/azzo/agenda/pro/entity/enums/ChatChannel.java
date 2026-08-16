package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;

/** Espelha {@code modules/chat/domain/enums/ChatChannel.java}. */
public enum ChatChannel {
  WHATSAPP,
  TELEGRAM;

  public static ChatChannel from(String value) {
    if (value == null || value.isBlank()) return WHATSAPP;
    try {
      return ChatChannel.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Canal de chat invalido: " + value);
    }
  }
}
