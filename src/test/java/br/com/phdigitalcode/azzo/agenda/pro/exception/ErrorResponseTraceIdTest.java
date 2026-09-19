package br.com.phdigitalcode.azzo.agenda.pro.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** O codigo do erro que o usuario reporta abre o trace e o log no Grafana (2026-09-19). */
class ErrorResponseTraceIdTest {

  @AfterEach
  void limpar() {
    MDC.clear();
  }

  @Test
  @DisplayName("a resposta de erro leva o traceId da requisicao")
  void levaOTraceIdDaRequisicao() {
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

    ErrorResponse erro = new ErrorResponse("BAD_REQUEST", "falhou", null, "/api/v1/x");

    assertThat(erro.traceId).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
  }

  @Test
  @DisplayName("sem tracing, o traceId fica nulo (e nao 'N/A')")
  void semTracingFicaNulo() {
    ErrorResponse erro = new ErrorResponse("BAD_REQUEST", "falhou", null, "/api/v1/x");

    assertThat(erro.traceId).isNull();
  }
}
