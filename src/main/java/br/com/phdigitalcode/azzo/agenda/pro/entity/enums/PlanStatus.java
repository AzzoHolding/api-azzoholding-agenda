package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

public enum PlanStatus {
  ACTIVE("Ativo"),
  EXPIRED("Vencido"),
  SUSPENDED("Suspenso"),
  CANCELED("Cancelado");

  private final String description;

  PlanStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static PlanStatus fromValue(String value) {
    if (value == null || value.isBlank()) return null;
    for (PlanStatus item : values()) {
      if (item.name().equalsIgnoreCase(value.trim())) return item;
      if (item.description.equalsIgnoreCase(value.trim())) return item;
    }
    throw new IllegalArgumentException("Status de plano invalido: " + value);
  }
}
