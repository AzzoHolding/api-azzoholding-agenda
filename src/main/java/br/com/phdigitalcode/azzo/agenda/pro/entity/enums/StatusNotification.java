package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

/** Espelha {@code modules/notifications/domain/enums/StatusNotification.java}. */
public enum StatusNotification {
  PENDING("Pendente"),
  SENT("Enviado"),
  FAILED("Falhou");

  private final String description;

  StatusNotification(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static StatusNotification fromValue(String value) {
    if (value == null || value.isBlank()) return null;
    for (StatusNotification item : values()) {
      if (item.name().equalsIgnoreCase(value.trim())) return item;
      if (item.description.equalsIgnoreCase(value.trim())) return item;
    }
    throw new IllegalArgumentException("Status de notificacao invalido: " + value);
  }
}
