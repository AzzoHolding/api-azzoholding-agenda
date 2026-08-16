package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

  private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

  @Test
  void aceitaSenhaComTodosOsRequisitos() {
    assertThat(validator.isValid("Senha@123")).isTrue();
  }

  @Test
  void rejeitaSenhaCurta() {
    assertThat(validator.isValid("Ab1@")).isFalse();
  }

  @Test
  void rejeitaSenhaSemMaiuscula() {
    assertThat(validator.isValid("senha@123")).isFalse();
  }

  @Test
  void rejeitaSenhaSemCaractereEspecial() {
    assertThat(validator.isValid("Senha1234")).isFalse();
  }

  @Test
  void rejeitaSenhaNula() {
    assertThat(validator.isValid(null)).isFalse();
  }

  @Test
  void validateOrThrowLancaExcecaoComMensagemDeRequisitos() {
    assertThatThrownBy(() -> validator.validateOrThrow("fraca"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minimo de 8 caracteres");
  }
}
