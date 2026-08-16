package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

public enum StatusSubscription {
  PENDING("Pendente"),
  ACTIVE("Ativo"),
  OVERDUE("Atrasado"),
  CANCELLED("Cancelado"),
  INACTIVE("Inativo");

  private final String description;

  StatusSubscription(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static StatusSubscription fromValue(String value) {
    if (value == null || value.isBlank()) return null;
    for (StatusSubscription item : values()) {
      if (item.name().equalsIgnoreCase(value.trim())) return item;
      if (item.description.equalsIgnoreCase(value.trim())) return item;
    }
    throw new IllegalArgumentException("Status de assinatura invalido: " + value);
  }
}
