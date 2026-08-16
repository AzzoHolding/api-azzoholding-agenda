package br.com.phdigitalcode.azzo.agenda.pro.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cobre o {@code CnpjValidator} portado (digitos verificadores, mascara e formatacao). */
class CnpjValidatorTest {

  private static final String CNPJ_VALIDO = "11222333000181";

  @Test
  void cnpjValidoPassaNaValidacaoDeDigitosVerificadores() {
    assertThat(CnpjValidator.isValid(CNPJ_VALIDO)).isTrue();
  }

  @Test
  void cnpjValidoComFormatacaoTambemPassa() {
    assertThat(CnpjValidator.isValid("11.222.333/0001-81")).isTrue();
  }

  @Test
  void cnpjComDigitoVerificadorErradoFalha() {
    assertThat(CnpjValidator.isValid("11222333000182")).isFalse();
  }

  @Test
  void cnpjComTodosOsDigitosIguaisFalha() {
    assertThat(CnpjValidator.isValid("11111111111111")).isFalse();
  }

  @Test
  void cnpjComTamanhoErradoFalha() {
    assertThat(CnpjValidator.isValid("112223330001")).isFalse();
    assertThat(CnpjValidator.isValid(null)).isFalse();
  }

  @Test
  void formatAplicaMascaraVisualPadrao() {
    assertThat(CnpjValidator.format(CNPJ_VALIDO)).isEqualTo("11.222.333/0001-81");
  }

  @Test
  void formatDeValorInvalidoDevolveEntradaOriginal() {
    assertThat(CnpjValidator.format("123")).isEqualTo("123");
  }

  @Test
  void maskNaoExpoeOsDigitosFinaisDoCnpj() {
    String masked = CnpjValidator.mask(CNPJ_VALIDO);
    assertThat(masked).startsWith("112223");
    assertThat(masked).doesNotContain("000181");
  }

  @Test
  void maskDeValorCurtoNaoVazaNada() {
    assertThat(CnpjValidator.mask("123")).isEqualTo("****");
  }

  @Test
  void sanitizeRemoveTudoQueNaoEhDigito() {
    assertThat(CnpjValidator.sanitize("11.222.333/0001-81")).isEqualTo(CNPJ_VALIDO);
  }
}
