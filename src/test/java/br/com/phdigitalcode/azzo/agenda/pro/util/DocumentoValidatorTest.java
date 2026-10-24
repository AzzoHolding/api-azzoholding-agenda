package br.com.phdigitalcode.azzo.agenda.pro.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentoValidatorTest {

  @Test
  void cpfValidoComESemMascara() {
    assertThat(DocumentoValidator.cpfValido("52998224725")).isTrue();
    assertThat(DocumentoValidator.cpfValido("529.982.247-25")).isTrue();
    assertThat(DocumentoValidator.cpfValido("12345678909")).isTrue();
  }

  @Test
  void cpfComDigitoErradoOuRepetidoOuTamanhoErradoEhInvalido() {
    assertThat(DocumentoValidator.cpfValido("52998224726")).isFalse(); // ultimo digito
    assertThat(DocumentoValidator.cpfValido("52998224735")).isFalse(); // penultimo digito
    assertThat(DocumentoValidator.cpfValido("11111111111")).isFalse();
    assertThat(DocumentoValidator.cpfValido("123")).isFalse();
    assertThat(DocumentoValidator.cpfValido(null)).isFalse();
  }

  @Test
  void cpfOuCnpjDecidePeloTamanho() {
    assertThat(DocumentoValidator.cpfOuCnpjValido("529.982.247-25")).isTrue();
    assertThat(DocumentoValidator.cpfOuCnpjValido("11.222.333/0001-81")).isTrue();
    assertThat(DocumentoValidator.cpfOuCnpjValido("11222333000182")).isFalse(); // digito errado
    assertThat(DocumentoValidator.cpfOuCnpjValido("1234567890123")).isFalse(); // 13 digitos
    assertThat(DocumentoValidator.cpfOuCnpjValido("")).isFalse();
  }
}
