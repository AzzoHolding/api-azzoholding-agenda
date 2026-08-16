package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.FiscalDocumentStatus;

/**
 * Espelha {@code modules/fiscal/domain/fiscal/FiscalDocumentStateMachine.java}.
 *
 * <p><b>{@link #canReach} e alcancabilidade, nao transicao direta.</b> O nome engana: o metodo faz
 * uma busca em largura sobre o grafo de transicoes, entao {@code canReach(DRAFT, AUTHORIZED)} e
 * {@code true} mesmo nao existindo aresta direta entre os dois — o caminho
 * {@code DRAFT → GENERATED → SIGNED → SUBMITTED → AUTHORIZED} existe. Quem precisa de transicao
 * direta nao pode usar este metodo. Comportamento do original, preservado e coberto por teste.
 *
 * <p>Sem estado mutavel: o mapa e {@code static final} e so lido, entao o bean singleton e seguro.
 */
@Component
public class FiscalDocumentStateMachine {

  private static final Map<FiscalDocumentStatus, Set<FiscalDocumentStatus>>
      ALLOWED_DIRECT_TRANSITIONS = new EnumMap<>(FiscalDocumentStatus.class);

  static {
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.DRAFT, EnumSet.of(FiscalDocumentStatus.GENERATED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.GENERATED, EnumSet.of(FiscalDocumentStatus.SIGNED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.SIGNED, EnumSet.of(FiscalDocumentStatus.SUBMITTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.SUBMITTED,
        EnumSet.of(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.REJECTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.AUTHORIZED, EnumSet.of(FiscalDocumentStatus.CANCEL_PENDING));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.REJECTED, EnumSet.of(FiscalDocumentStatus.DRAFT));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.CANCEL_PENDING, EnumSet.of(FiscalDocumentStatus.CANCELLED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.CANCELLED, EnumSet.noneOf(FiscalDocumentStatus.class));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.CONTINGENCY_PENDING, EnumSet.of(FiscalDocumentStatus.SUBMITTED));
    ALLOWED_DIRECT_TRANSITIONS.put(
        FiscalDocumentStatus.INUTILIZED, EnumSet.noneOf(FiscalDocumentStatus.class));
  }

  /**
   * {@code true} se existe <b>algum caminho</b> de {@code from} ate {@code to}. Nulo em qualquer
   * ponta e {@code false}; origem igual ao destino e {@code true} sem consultar o grafo.
   */
  public boolean canReach(FiscalDocumentStatus from, FiscalDocumentStatus to) {
    if (from == null || to == null) return false;
    if (from == to) return true;

    Set<FiscalDocumentStatus> visited = EnumSet.noneOf(FiscalDocumentStatus.class);
    Deque<FiscalDocumentStatus> queue = new ArrayDeque<>();
    queue.add(from);
    visited.add(from);

    while (!queue.isEmpty()) {
      FiscalDocumentStatus current = queue.poll();
      Set<FiscalDocumentStatus> nextStates =
          ALLOWED_DIRECT_TRANSITIONS.getOrDefault(
              current, EnumSet.noneOf(FiscalDocumentStatus.class));
      for (FiscalDocumentStatus next : nextStates) {
        if (next == to) return true;
        if (visited.add(next)) queue.add(next);
      }
    }
    return false;
  }
}
