package br.com.phdigitalcode.azzo.agenda.pro.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TelefoneBrTest {

  @Test
  void aceitaFixoCelularEOsDoisComO55() {
    assertThat(TelefoneBr.normalizarOuFalhar("(21) 3333-4444")).isEqualTo("2133334444");
    assertThat(TelefoneBr.normalizarOuFalhar("(21) 98759-9613")).isEqualTo("21987599613");
    assertThat(TelefoneBr.normalizarOuFalhar("+55 21 3333-4444")).isEqualTo("552133334444");
    assertThat(TelefoneBr.normalizarOuFalhar("5521987599613")).isEqualTo("5521987599613");
  }

  @Test
  void vazioEOpcional() {
    assertThat(TelefoneBr.normalizarOuFalhar(null)).isNull();
    assertThat(TelefoneBr.normalizarOuFalhar("")).isNull();
    assertThat(TelefoneBr.normalizarOuFalhar(" () - ")).isNull();
  }

  @Test
  void recusaTrezeDigitosSemO55() {
    // O caso do teste ponta a ponta de 21/10/2026.
    assertThatThrownBy(() -> TelefoneBr.normalizarOuFalhar("2198759961399"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(TelefoneBr.MENSAGEM_INVALIDO);
  }

  @Test
  void recusaCurtoOuLongoDemais() {
    assertThatThrownBy(() -> TelefoneBr.normalizarOuFalhar("98759961"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TelefoneBr.normalizarOuFalhar("55219875996130"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void numeroJaGravadoNaoTravaAEdicao() {
    // Estrangeiro gravado pelo WhatsApp: editar o resto do cadastro nao pode exigir trocar o numero.
    assertThat(TelefoneBr.normalizarSeMudou("+351912345678", "351912345678")).isEqualTo("351912345678");
    assertThatThrownBy(() -> TelefoneBr.normalizarSeMudou("2198759961399", "21987599613"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(TelefoneBr.normalizarSeMudou("", "21987599613")).isNull();
  }
}
