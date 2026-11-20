package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.Locale;

/** Espelha {@code modules/chat/domain/enums/ChatChannel.java}. */
public enum ChatChannel {
  WHATSAPP,
  TELEGRAM,
  /** Conversa pela area da cliente (V188): a mensagem entra e sai pelo banco, sem provedor externo. */
  AREA_CLIENTE;

  public static ChatChannel from(String value) {
    if (value == null || value.isBlank()) return WHATSAPP;
    try {
      return ChatChannel.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Canal de chat inválido: " + value);
    }
  }
}
