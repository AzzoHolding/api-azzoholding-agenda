package br.com.phdigitalcode.azzo.agenda.pro.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SchedulingDtos.AgendamentoRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Sem estas anotacoes, um pedido sem cliente ou profissional estourava NPE no service e voltava
 * "Ocorreu um erro inesperado" — a tela nao tinha como dizer o que faltou.
 */
class AgendamentoRequestValidacaoTest {

  private static final Validator VALIDADOR = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void pedidoVazioDizCadaCampoObrigatorio() {
    assertThat(VALIDADOR.validate(new AgendamentoRequest()))
        .extracting(ConstraintViolation::getMessage)
        .containsExactlyInAnyOrder(
            "Cliente é obrigatório",
            "Profissional é obrigatório",
            "Data do agendamento é obrigatória",
            "Hora de início é obrigatória");
  }

  @Test
  void pedidoCompletoPassa() {
    AgendamentoRequest req = new AgendamentoRequest();
    req.clientId = "c1";
    req.professionalId = "p1";
    req.date = "2030-06-10";
    req.startTime = "10:00";

    assertThat(VALIDADOR.validate(req)).isEmpty();
  }
}
