package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O salao escreve a mensagem dele; a Meta exige {@code {{1}}, {{2}}...} numerados e um exemplo de
 * cada. A ORDEM e o contrato: e ela que liga {@code {{1}}} ao nome do cliente no envio, e trocar
 * duas posicoes manda o servico no lugar do nome sem a Meta reclamar.
 */
class ModeloParaTemplateTest {

  @Test
  @DisplayName("numera as variaveis na ordem em que aparecem")
  void numeraNaOrdemDeAparicao() {
    ModeloParaTemplate.Convertido convertido =
        ModeloParaTemplate.converter("Olá {cliente}! Seu {servico} é dia {data} às {hora}.");

    assertThat(convertido.corpo()).isEqualTo("Olá {{1}}! Seu {{2}} é dia {{3}} às {{4}}.");
    assertThat(convertido.variaveis()).containsExactly("cliente", "servico", "data", "hora");
    // A Meta EXIGE um exemplo por variavel para analisar.
    assertThat(convertido.exemplos()).hasSize(4);
  }

  /** Escrever o nome duas vezes e a mesma pergunta, e nao duas. */
  @Test
  @DisplayName("variavel repetida reaproveita o mesmo numero")
  void repetidaUsaOMesmoNumero() {
    ModeloParaTemplate.Convertido convertido =
        ModeloParaTemplate.converter("{cliente}, confirmamos. Até logo, {cliente}!");

    assertThat(convertido.corpo()).isEqualTo("{{1}}, confirmamos. Até logo, {{1}}!");
    assertThat(convertido.variaveis()).containsExactly("cliente");
  }

  /**
   * Marcador desconhecido fica literal: melhor a Meta recusar um texto estranho do que o sistema
   * inventar uma variavel que ninguem sabe preencher na hora do envio.
   */
  @Test
  @DisplayName("marcador que nao conhecemos fica como esta")
  void desconhecidoFicaLiteral() {
    ModeloParaTemplate.Convertido convertido =
        ModeloParaTemplate.converter("Oi {cliente}, seu {codigo_secreto} chegou.");

    assertThat(convertido.corpo()).isEqualTo("Oi {{1}}, seu {codigo_secreto} chegou.");
    assertThat(convertido.variaveis()).containsExactly("cliente");
  }

  @Test
  @DisplayName("texto sem variavel nenhuma passa inteiro")
  void semVariavelPassaInteiro() {
    ModeloParaTemplate.Convertido convertido =
        ModeloParaTemplate.converter("Seu horário está confirmado.");

    assertThat(convertido.corpo()).isEqualTo("Seu horário está confirmado.");
    assertThat(convertido.variaveis()).isEmpty();
    assertThat(convertido.exemplos()).isEmpty();
  }

  /** Template sem corpo seria recusado pela Meta; recusar aqui diz o motivo de verdade. */
  @Test
  @DisplayName("modelo vazio nao vira template")
  void vazioNaoViraTemplate() {
    assertThatThrownBy(() -> ModeloParaTemplate.converter("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vazio");
  }
}
