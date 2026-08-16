package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

public enum StatusCheckout {
  PENDING("Pendente"),
  CONFIRMED("Confirmado"),
  EXPIRED("Expirado"),
  CANCELLED("Cancelado"),
  FAILED("Falhou");

  private final String description;

  StatusCheckout(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static StatusCheckout fromValue(String value) {
    if (value == null || value.isBlank()) return null;
    for (StatusCheckout item : values()) {
      if (item.name().equalsIgnoreCase(value.trim())) return item;
      if (item.description.equalsIgnoreCase(value.trim())) return item;
    }
    throw new IllegalArgumentException("Status de checkout invalido: " + value);
  }
}
