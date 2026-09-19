package br.com.phdigitalcode.azzo.agenda.pro.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TracingConfigTest {

  private final ObservationPredicate filtro = new TracingConfig().semTraceDoActuator();

  private boolean vira_trace(String uri) {
    return filtro.test(
        "http.server.requests",
        new ServerRequestObservationContext(
            new MockHttpServletRequest("GET", uri), new MockHttpServletResponse()));
  }

  @Test
  @DisplayName("coleta do Prometheus e health nao viram trace; as rotas do sistema viram")
  void soAsRotasDoSistemaViramTrace() {
    assertThat(vira_trace("/actuator/prometheus")).isFalse();
    assertThat(vira_trace("/actuator/health")).isFalse();
    assertThat(vira_trace("/api/v1/appointments")).isTrue();
  }
}
