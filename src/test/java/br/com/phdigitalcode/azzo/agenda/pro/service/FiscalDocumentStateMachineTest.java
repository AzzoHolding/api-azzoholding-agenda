package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.FiscalDocumentStatus;

/**
 * Cobre {@code modules/fiscal/domain/fiscal/FiscalDocumentStateMachine.java}.
 *
 * <p>O ponto central e que {@code canReach} responde <b>alcancabilidade</b>, nao transicao direta —
 * comportamento facil de confundir e que os testes abaixo travam explicitamente.
 */
class FiscalDocumentStateMachineTest {

  private final FiscalDocumentStateMachine machine = new FiscalDocumentStateMachine();

  // ─── transicoes diretas do grafo ──────────────────────────────────────────

  @Test
  void oCaminhoFelizAvancaUmPassoDeCadaVez() {
    assertThat(machine.canReach(FiscalDocumentStatus.DRAFT, FiscalDocumentStatus.GENERATED)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.GENERATED, FiscalDocumentStatus.SIGNED)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.SIGNED, FiscalDocumentStatus.SUBMITTED)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.SUBMITTED, FiscalDocumentStatus.AUTHORIZED))
        .isTrue();
  }

  @Test
  void submittedBifurcaEmAuthorizedERejected() {
    assertThat(machine.canReach(FiscalDocumentStatus.SUBMITTED, FiscalDocumentStatus.AUTHORIZED))
        .isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.SUBMITTED, FiscalDocumentStatus.REJECTED))
        .isTrue();
  }

  @Test
  void oCancelamentoPassaObrigatoriamentePorCancelPending() {
    assertThat(machine.canReach(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.CANCEL_PENDING))
        .isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.CANCEL_PENDING, FiscalDocumentStatus.CANCELLED))
        .isTrue();
  }

  @Test
  void contingenciaReentraNoFluxoPorSubmitted() {
    assertThat(
            machine.canReach(
                FiscalDocumentStatus.CONTINGENCY_PENDING, FiscalDocumentStatus.SUBMITTED))
        .isTrue();
    assertThat(
            machine.canReach(
                FiscalDocumentStatus.CONTINGENCY_PENDING, FiscalDocumentStatus.AUTHORIZED))
        .isTrue();
  }

  // ─── alcancabilidade: o que o nome do metodo esconde ──────────────────────

  /**
   * Nao existe aresta direta {@code DRAFT → AUTHORIZED}, mas existe caminho. Quem precisar validar
   * transicao direta <b>nao pode</b> usar {@code canReach}.
   */
  @Test
  void canReachEAlcancabilidadeENaoTransicaoDireta() {
    assertThat(machine.canReach(FiscalDocumentStatus.DRAFT, FiscalDocumentStatus.AUTHORIZED)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.DRAFT, FiscalDocumentStatus.CANCELLED)).isTrue();
  }

  /** {@code REJECTED → DRAFT} fecha um ciclo; a busca em largura nao pode entrar em loop. */
  @Test
  void oCicloRejectedDraftNaoTravaABusca() {
    assertThat(machine.canReach(FiscalDocumentStatus.REJECTED, FiscalDocumentStatus.DRAFT)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.REJECTED, FiscalDocumentStatus.AUTHORIZED))
        .isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.REJECTED, FiscalDocumentStatus.REJECTED))
        .isTrue();
  }

  // ─── estados terminais ────────────────────────────────────────────────────

  @Test
  void cancelledNaoAlcancaNadaAlemDeSiMesmo() {
    for (FiscalDocumentStatus destino : FiscalDocumentStatus.values()) {
      boolean esperado = destino == FiscalDocumentStatus.CANCELLED;
      assertThat(machine.canReach(FiscalDocumentStatus.CANCELLED, destino))
          .as("CANCELLED -> %s", destino)
          .isEqualTo(esperado);
    }
  }

  @Test
  void inutilizedNaoAlcancaNadaAlemDeSiMesmo() {
    for (FiscalDocumentStatus destino : FiscalDocumentStatus.values()) {
      boolean esperado = destino == FiscalDocumentStatus.INUTILIZED;
      assertThat(machine.canReach(FiscalDocumentStatus.INUTILIZED, destino))
          .as("INUTILIZED -> %s", destino)
          .isEqualTo(esperado);
    }
  }

  /** Nenhum estado leva a {@code INUTILIZED}: ele so pode ser atribuido diretamente. */
  @Test
  void inutilizedNaoEAlcancavelAPartirDeNenhumOutroEstado() {
    for (FiscalDocumentStatus origem : FiscalDocumentStatus.values()) {
      if (origem == FiscalDocumentStatus.INUTILIZED) continue;
      assertThat(machine.canReach(origem, FiscalDocumentStatus.INUTILIZED))
          .as("%s -> INUTILIZED", origem)
          .isFalse();
    }
  }

  /** Nenhum estado leva a {@code CONTINGENCY_PENDING}: e ponto de entrada, nao de chegada. */
  @Test
  void contingencyPendingNaoEAlcancavelAPartirDeNenhumOutroEstado() {
    for (FiscalDocumentStatus origem : FiscalDocumentStatus.values()) {
      if (origem == FiscalDocumentStatus.CONTINGENCY_PENDING) continue;
      assertThat(machine.canReach(origem, FiscalDocumentStatus.CONTINGENCY_PENDING))
          .as("%s -> CONTINGENCY_PENDING", origem)
          .isFalse();
    }
  }

  // ─── nao se volta atras ───────────────────────────────────────────────────

  /**
   * Depois de {@code AUTHORIZED} o unico caminho e {@code CANCEL_PENDING → CANCELLED}, que e beco
   * sem saida — nada do inicio do fluxo volta a ser alcancavel.
   */
  @Test
  void oFluxoNaoRetrocedeDepoisDeAuthorized() {
    assertThat(machine.canReach(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.DRAFT))
        .isFalse();
    assertThat(machine.canReach(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.GENERATED))
        .isFalse();
    assertThat(machine.canReach(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.SIGNED))
        .isFalse();
    assertThat(machine.canReach(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.SUBMITTED))
        .isFalse();
  }

  /**
   * ⚠️ Consequencia nao obvia da aresta de reemissao {@code REJECTED → DRAFT}: <b>antes</b> de
   * autorizar, o fluxo e um ciclo. {@code SUBMITTED} alcanca {@code GENERATED} de volta pelo
   * caminho {@code SUBMITTED → REJECTED → DRAFT → GENERATED}. Nao e defeito — e o que permite
   * corrigir e reenviar uma nota rejeitada —, mas confirma que {@code canReach} <b>nao serve</b>
   * para decidir se uma transicao pontual e legitima.
   */
  @Test
  void antesDeAutorizarOGrafoEUmCicloEQuaseTudoSeAlcanca() {
    assertThat(machine.canReach(FiscalDocumentStatus.SUBMITTED, FiscalDocumentStatus.GENERATED))
        .isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.SUBMITTED, FiscalDocumentStatus.DRAFT)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.GENERATED, FiscalDocumentStatus.DRAFT)).isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.SIGNED, FiscalDocumentStatus.GENERATED))
        .isTrue();
  }

  @Test
  void authorizedNaoPulaDiretoParaCancelledNemAoContrario() {
    // AUTHORIZED alcanca CANCELLED, mas passando por CANCEL_PENDING.
    assertThat(machine.canReach(FiscalDocumentStatus.AUTHORIZED, FiscalDocumentStatus.CANCELLED))
        .isTrue();
    assertThat(machine.canReach(FiscalDocumentStatus.CANCELLED, FiscalDocumentStatus.AUTHORIZED))
        .isFalse();
  }

  // ─── bordas ───────────────────────────────────────────────────────────────

  @Test
  void nuloEmQualquerPontaEFalse() {
    assertThat(machine.canReach(null, FiscalDocumentStatus.DRAFT)).isFalse();
    assertThat(machine.canReach(FiscalDocumentStatus.DRAFT, null)).isFalse();
    assertThat(machine.canReach(null, null)).isFalse();
  }

  @Test
  void origemIgualAoDestinoESempreTrueMesmoParaEstadoTerminal() {
    for (FiscalDocumentStatus status : FiscalDocumentStatus.values()) {
      assertThat(machine.canReach(status, status)).as("%s -> %s", status, status).isTrue();
    }
  }
}
