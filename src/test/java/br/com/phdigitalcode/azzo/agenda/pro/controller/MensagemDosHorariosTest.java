package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Analise de 15/11/2026, B1: horarios livres sem parametro respondiam mensagem de NullPointer. */
class MensagemDosHorariosTest {

  @Test
  @DisplayName("sem profissional, sem data ou com data errada: frase em portugues")
  void parametroFaltandoViraFrase() {
    NullPointerException npe = new NullPointerException("Cannot invoke \"String.length()\" because \"name\" is null");
    assertThat(AgendamentosController.mensagemDosHorarios(null, "2026-11-20", npe)).isEqualTo("Informe o profissional.");
    assertThat(AgendamentosController.mensagemDosHorarios("p", null, npe)).isEqualTo("Informe a data.");
    assertThat(AgendamentosController.mensagemDosHorarios(
            "p", "20/11", new DateTimeParseException("x", "20/11", 0)))
        .isEqualTo("Data inválida. Use o formato aaaa-mm-dd.");
    assertThat(AgendamentosController.mensagemDosHorarios(
            "abc", "2026-11-20", new IllegalArgumentException("Invalid UUID string: abc")))
        .isEqualTo("Identificador inválido.");
  }

  @Test
  @DisplayName("a mensagem do servico (salao fechado, etc.) continua")
  void mensagemDoServicoContinua() {
    assertThat(AgendamentosController.mensagemDosHorarios(
            "p", "2026-11-20", new IllegalArgumentException("Salão fechado na data informada")))
        .isEqualTo("Salão fechado na data informada");
  }
}
