package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import java.util.EnumSet;
import java.util.Map;

/**
 * Espelha {@code modules/scheduling/domain/enums/StatusAgendamento.java} — porte verbatim,
 * incluindo a maquina de transicoes permitidas e o {@code fromValue} que aceita tanto o nome do
 * enum quanto a descricao em portugues (a coluna {@code appointments.status} guarda a
 * <b>descricao</b>, ver {@code StatusAgendamentoConverter}).
 */
public enum StatusAgendamento {
  PENDING("Pendente"),
  CONFIRMED("Confirmado"),
  IN_PROGRESS("Em andamento"),
  COMPLETED("Concluido"),
  CANCELLED("Cancelado"),
  NO_SHOW("Nao compareceu");

  private static final Map<StatusAgendamento, EnumSet<StatusAgendamento>> ALLOWED_TRANSITIONS = Map.of(
      PENDING, EnumSet.of(CONFIRMED, CANCELLED, NO_SHOW),
      CONFIRMED, EnumSet.of(IN_PROGRESS, CANCELLED, NO_SHOW),
      IN_PROGRESS, EnumSet.of(COMPLETED, CANCELLED),
      COMPLETED, EnumSet.of(CANCELLED),
      CANCELLED, EnumSet.noneOf(StatusAgendamento.class),
      NO_SHOW, EnumSet.noneOf(StatusAgendamento.class));

  private final String description;

  StatusAgendamento(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public boolean canTransitionTo(StatusAgendamento nextStatus) {
    if (nextStatus == null) return false;
    if (this == nextStatus) return true;
    EnumSet<StatusAgendamento> allowed = ALLOWED_TRANSITIONS.get(this);
    return allowed != null && allowed.contains(nextStatus);
  }

  public static void validarTransicao(StatusAgendamento atual, StatusAgendamento proximo) {
    if (atual == null || proximo == null) {
      throw new IllegalArgumentException("Status de agendamento obrigatorio");
    }
    if (!atual.canTransitionTo(proximo)) {
      throw new IllegalArgumentException(
          "Transicao de status invalida: " + atual.name() + " -> " + proximo.name());
    }
  }

  public static StatusAgendamento fromValue(String value) {
    if (value == null || value.isBlank()) return null;
    for (StatusAgendamento item : values()) {
      if (item.name().equalsIgnoreCase(value.trim())) return item;
      if (item.description.equalsIgnoreCase(value.trim())) return item;
    }
    throw new IllegalArgumentException("Status de agendamento invalido: " + value);
  }
}
