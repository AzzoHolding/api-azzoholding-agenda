package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;

/**
 * Espelha {@code modules/nfse/domain/nfse/NfseFiscalStateMachine.java}.
 *
 * <p><b>{@link #canReach} e alcancabilidade (BFS), nao transicao direta</b> — mesma armadilha #12
 * ja documentada em {@link FiscalDocumentStateMachine}. Grafo:
 * {@code DRAFT -> READY_TO_SEND -> SIGNED -> SUBMITTED -> {PENDING, AUTHORIZED, REJECTED}},
 * {@code PENDING -> {AUTHORIZED, REJECTED}}, {@code AUTHORIZED -> CANCEL_PENDING ->
 * {CANCELLED, CANCEL_REJECTED}}, {@code CANCEL_REJECTED -> CANCEL_PENDING} (retentativa),
 * {@code REJECTED -> DRAFT} (reemissao, ciclico), {@code CANCELLED} terminal.
 *
 * <p>Sem estado mutavel: mapa {@code static final}, bean singleton seguro.
 */
@Component
public class NfseFiscalStateMachine {

  private static final Map<NfseFiscalStatus, Set<NfseFiscalStatus>> ALLOWED_DIRECT_TRANSITIONS =
      new EnumMap<>(NfseFiscalStatus.class);

  static {
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.DRAFT, EnumSet.of(NfseFiscalStatus.READY_TO_SEND));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.READY_TO_SEND, EnumSet.of(NfseFiscalStatus.SIGNED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.SIGNED, EnumSet.of(NfseFiscalStatus.SUBMITTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.SUBMITTED,
        EnumSet.of(NfseFiscalStatus.PENDING, NfseFiscalStatus.AUTHORIZED, NfseFiscalStatus.REJECTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.PENDING,
        EnumSet.of(NfseFiscalStatus.AUTHORIZED, NfseFiscalStatus.REJECTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.AUTHORIZED, EnumSet.of(NfseFiscalStatus.CANCEL_PENDING));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.CANCEL_PENDING,
        EnumSet.of(NfseFiscalStatus.CANCELLED, NfseFiscalStatus.CANCEL_REJECTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.CANCEL_REJECTED, EnumSet.of(NfseFiscalStatus.CANCEL_PENDING));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.REJECTED, EnumSet.of(NfseFiscalStatus.DRAFT));
    ALLOWED_DIRECT_TRANSITIONS.put(
        NfseFiscalStatus.CANCELLED, EnumSet.noneOf(NfseFiscalStatus.class));
  }

  /**
   * {@code true} se existe algum caminho de {@code from} ate {@code to}. Nulo em qualquer ponta e
   * {@code false}; origem igual ao destino e {@code true} sem consultar o grafo.
   */
  public boolean canReach(NfseFiscalStatus from, NfseFiscalStatus to) {
    if (from == null || to == null) return false;
    if (from == to) return true;

    Set<NfseFiscalStatus> visited = EnumSet.noneOf(NfseFiscalStatus.class);
    Deque<NfseFiscalStatus> queue = new ArrayDeque<>();
    queue.add(from);
    visited.add(from);

    while (!queue.isEmpty()) {
      NfseFiscalStatus current = queue.poll();
      Set<NfseFiscalStatus> nextStates =
          ALLOWED_DIRECT_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(NfseFiscalStatus.class));
      for (NfseFiscalStatus next : nextStates) {
        if (next == to) return true;
        if (visited.add(next)) queue.add(next);
      }
    }
    return false;
  }
}
