package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;

/** Espelha {@code NfseFiscalStateMachineUnitTest} do original (fronteira 1). */
class NfseFiscalStateMachineUnitTest {

  @Test
  void devePermitirFluxoPrincipalAteAutorizacao() {
    NfseFiscalStateMachine machine = new NfseFiscalStateMachine();
    assertTrue(machine.canReach(NfseFiscalStatus.DRAFT, NfseFiscalStatus.READY_TO_SEND));
    assertTrue(machine.canReach(NfseFiscalStatus.READY_TO_SEND, NfseFiscalStatus.SIGNED));
    assertTrue(machine.canReach(NfseFiscalStatus.SIGNED, NfseFiscalStatus.SUBMITTED));
    assertTrue(machine.canReach(NfseFiscalStatus.SUBMITTED, NfseFiscalStatus.AUTHORIZED));
  }

  @Test
  void deveBloquearRetornoDeStatusFinal() {
    NfseFiscalStateMachine machine = new NfseFiscalStateMachine();
    assertFalse(machine.canReach(NfseFiscalStatus.CANCELLED, NfseFiscalStatus.DRAFT));
    assertFalse(machine.canReach(NfseFiscalStatus.AUTHORIZED, NfseFiscalStatus.DRAFT));
  }

  @Test
  void devePermitirAlcancarCancelReRejectedViaReattempt() {
    // Assimetria do original: CANCEL_PENDING -> CANCEL_REJECTED existe no grafo, mas
    // NfseService.cancelar() (fronteira 6) so persiste CANCELLED em sucesso — na pratica o estado
    // fica inalcancavel em disco. A maquina de estados, isolada, ainda o alcanca (alcancabilidade).
    NfseFiscalStateMachine machine = new NfseFiscalStateMachine();
    assertTrue(machine.canReach(NfseFiscalStatus.CANCEL_PENDING, NfseFiscalStatus.CANCEL_REJECTED));
    assertTrue(machine.canReach(NfseFiscalStatus.CANCEL_REJECTED, NfseFiscalStatus.CANCEL_PENDING));
  }

  @Test
  void deveAlcancarAutorizadoAPartirDoRascunhoPorCaminhoIndireto() {
    // canReach e alcancabilidade (BFS), nao transicao direta — armadilha #12 do briefing.
    NfseFiscalStateMachine machine = new NfseFiscalStateMachine();
    assertTrue(machine.canReach(NfseFiscalStatus.DRAFT, NfseFiscalStatus.AUTHORIZED));
  }

  @Test
  void devePermitirReemissaoCiclicaDeRejeitadoParaRascunho() {
    NfseFiscalStateMachine machine = new NfseFiscalStateMachine();
    assertTrue(machine.canReach(NfseFiscalStatus.REJECTED, NfseFiscalStatus.DRAFT));
    assertTrue(machine.canReach(NfseFiscalStatus.REJECTED, NfseFiscalStatus.AUTHORIZED));
  }
}
