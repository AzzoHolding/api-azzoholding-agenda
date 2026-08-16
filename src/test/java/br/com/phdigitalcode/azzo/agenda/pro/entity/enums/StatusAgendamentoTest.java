package br.com.phdigitalcode.azzo.agenda.pro.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusAgendamentoTest {

  @Test
  @DisplayName("fromValue aceita o nome do enum e tambem a descricao em portugues gravada na coluna")
  void fromValueAceitaNomeEDescricao() {
    assertThat(StatusAgendamento.fromValue("NO_SHOW")).isEqualTo(StatusAgendamento.NO_SHOW);
    assertThat(StatusAgendamento.fromValue("Nao compareceu")).isEqualTo(StatusAgendamento.NO_SHOW);
    assertThat(StatusAgendamento.fromValue("concluido")).isEqualTo(StatusAgendamento.COMPLETED);
    assertThat(StatusAgendamento.fromValue("  Em andamento ")).isEqualTo(StatusAgendamento.IN_PROGRESS);
  }

  @Test
  @DisplayName("fromValue devolve null para vazio e falha para valor desconhecido")
  void fromValueNuloEInvalido() {
    assertThat(StatusAgendamento.fromValue(null)).isNull();
    assertThat(StatusAgendamento.fromValue("   ")).isNull();
    assertThatThrownBy(() -> StatusAgendamento.fromValue("ARQUIVADO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Status de agendamento invalido");
  }

  @Test
  @DisplayName("A coluna appointments.status guarda a descricao, nao o nome do enum")
  void descricaoEOQueVaiParaOBanco() {
    assertThat(StatusAgendamento.COMPLETED.getDescription()).isEqualTo("Concluido");
    assertThat(StatusAgendamento.NO_SHOW.getDescription()).isEqualTo("Nao compareceu");
    assertThat(StatusAgendamento.CONFIRMED.getDescription()).isEqualTo("Confirmado");
  }

  @Test
  @DisplayName("Transicoes permitidas seguem a maquina de estados do original")
  void transicoesPermitidas() {
    assertThat(StatusAgendamento.PENDING.canTransitionTo(StatusAgendamento.CONFIRMED)).isTrue();
    assertThat(StatusAgendamento.CONFIRMED.canTransitionTo(StatusAgendamento.IN_PROGRESS)).isTrue();
    assertThat(StatusAgendamento.IN_PROGRESS.canTransitionTo(StatusAgendamento.COMPLETED)).isTrue();
    assertThat(StatusAgendamento.COMPLETED.canTransitionTo(StatusAgendamento.CANCELLED)).isTrue();

    // PENDING nao pode pular direto para IN_PROGRESS nem COMPLETED
    assertThat(StatusAgendamento.PENDING.canTransitionTo(StatusAgendamento.IN_PROGRESS)).isFalse();
    assertThat(StatusAgendamento.PENDING.canTransitionTo(StatusAgendamento.COMPLETED)).isFalse();

    // CANCELLED e NO_SHOW sao terminais
    assertThat(StatusAgendamento.CANCELLED.canTransitionTo(StatusAgendamento.PENDING)).isFalse();
    assertThat(StatusAgendamento.NO_SHOW.canTransitionTo(StatusAgendamento.CONFIRMED)).isFalse();
  }

  @Test
  @DisplayName("Transicao para o mesmo status e sempre permitida (idempotencia)")
  void transicaoParaOMesmoStatus() {
    for (StatusAgendamento status : StatusAgendamento.values()) {
      assertThat(status.canTransitionTo(status)).isTrue();
    }
  }

  @Test
  @DisplayName("validarTransicao lanca com a mensagem exata do original")
  void validarTransicaoInvalida() {
    assertThatThrownBy(
            () -> StatusAgendamento.validarTransicao(StatusAgendamento.CANCELLED, StatusAgendamento.CONFIRMED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Transicao de status invalida: CANCELLED -> CONFIRMED");

    assertThatThrownBy(() -> StatusAgendamento.validarTransicao(null, StatusAgendamento.CONFIRMED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Status de agendamento obrigatorio");

    assertDoesNotThrow(
        () -> StatusAgendamento.validarTransicao(StatusAgendamento.PENDING, StatusAgendamento.CANCELLED));
  }
}
