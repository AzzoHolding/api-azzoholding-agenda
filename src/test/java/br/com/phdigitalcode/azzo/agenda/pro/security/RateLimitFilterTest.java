package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import br.com.phdigitalcode.azzo.agenda.pro.security.RateLimitFilter.RateLimitRule;
import jakarta.servlet.FilterChain;

/** SEG-002: o limite por IP vale para qualquer grafia do caminho, nao so a literal. */
class RateLimitFilterTest {

  private RateLimitFilter filtro;
  private FilterChain cadeia;

  @BeforeEach
  void preparar() {
    filtro =
        new RateLimitFilter(
            Map.of("/api/v1/auth/login", new RateLimitRule("auth-login", 2, Duration.ofMinutes(3))));
    cadeia = mock(FilterChain.class);
  }

  private int post(String uri) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRequestURI(uri);
    request.setRemoteAddr("203.0.113.9");
    MockHttpServletResponse resposta = new MockHttpServletResponse();
    filtro.doFilter(request, resposta, cadeia);
    return resposta.getStatus();
  }

  @Test
  void caminhoLiteralEhLimitado() throws Exception {
    assertThat(post("/api/v1/auth/login")).isEqualTo(200);
    assertThat(post("/api/v1/auth/login")).isEqualTo(200);
    assertThat(post("/api/v1/auth/login")).isEqualTo(429);
  }

  /** O achado: "%6Cogin" caia fora da regra e nunca tomava 429. */
  @Test
  void caminhoCodificadoDividaOMesmoBalde() throws Exception {
    assertThat(post("/api/v1/auth/%6Cogin")).isEqualTo(200);
    assertThat(post("/api/v1/auth/login")).isEqualTo(200);
    assertThat(post("/api/v1/auth/%6cogin")).isEqualTo(429);
    assertThat(post("/api/v1/auth/login;x=1")).isEqualTo(429);
    assertThat(post("/api/v1/auth/login/")).isEqualTo(429);
  }

  @Test
  void rotaSemRegraNaoELimitada() throws Exception {
    for (int i = 0; i < 5; i++) assertThat(post("/api/v1/auth/refresh")).isEqualTo(200);
    verify(cadeia, times(5)).doFilter(any(), any());
  }
}
