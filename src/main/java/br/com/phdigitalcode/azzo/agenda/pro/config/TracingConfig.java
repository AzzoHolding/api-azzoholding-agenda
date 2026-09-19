package br.com.phdigitalcode.azzo.agenda.pro.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * O que NAO vira trace: as rotas do actuator. O Prometheus coleta /actuator/prometheus a cada 30 s
 * e o Docker consulta /actuator/health — sem este filtro, o Tempo encheria de requisicoes que nao
 * dizem nada sobre o uso do sistema.
 */
@Configuration
public class TracingConfig {

  @Bean
  ObservationPredicate semTraceDoActuator() {
    return (name, context) -> {
      if (context instanceof ServerRequestObservationContext servidor) {
        String uri = servidor.getCarrier().getRequestURI();
        return uri == null || !uri.startsWith("/actuator");
      }
      return true;
    };
  }
}
