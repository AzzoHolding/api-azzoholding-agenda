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
        .hasMessageContaining("mínimo de 8 caracteres");
  }

  @Test
  void contaBytesEnaoLetras_oBcryptCortariaOResto() {
    String setentaAscii = "Aa1@" + "x".repeat(68);
    assertThat(validator.isValid(setentaAscii)).isTrue();
    // 40 letras com acento = 80 bytes: passava no limite de 72 LETRAS e era cortada sem aviso.
    String comAcento = "Aa1@" + "é".repeat(40);
    assertThat(comAcento.length()).isLessThanOrEqualTo(72);
    assertThat(validator.isValid(comAcento)).isFalse();
    assertThatThrownBy(() -> validator.validateOrThrow(comAcento))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("72 caracteres")
        .hasMessageNotContaining("enha");
  }
}
