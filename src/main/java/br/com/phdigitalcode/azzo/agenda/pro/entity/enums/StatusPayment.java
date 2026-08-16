package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

public enum StatusPayment {
  PENDING("Pendente"),
  RECEIVED("Recebido"),
  CONFIRMED("Confirmado"),
  OVERDUE("Atrasado"),
  CANCELLED("Cancelado"),
  REFUNDED("Estornado"),
  FAILED("Falhou"),
  OTHER("Outro");

  private final String description;

  StatusPayment(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static StatusPayment fromValue(String value) {
    if (value == null || value.isBlank()) return null;
    for (StatusPayment item : values()) {
      if (item.name().equalsIgnoreCase(value.trim())) return item;
      if (item.description.equalsIgnoreCase(value.trim())) return item;
    }
    return OTHER;
  }
}
