package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** O Spring precisa conseguir criar o filtro sozinho (queda de 20/10/2026: dois construtores sem @Autowired). */
class InternalApiKeyFilterSpringTest {

  @Test
  void springCriaOFiltro() {
    try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(InternalApiKeyFilter.class)) {
      assertThat(ctx.getBean(InternalApiKeyFilter.class)).isNotNull();
    }
  }
}
