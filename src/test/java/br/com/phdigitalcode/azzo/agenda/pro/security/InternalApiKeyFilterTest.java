package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

/**
 * Cobre o porte de {@code InternalApiKeyFilter}: as rotas {@code /api/v1/internal/*} estao em
 * {@code permitAll} no {@code SecurityConfig}, entao este filtro e a unica autenticacao delas.
 */
class InternalApiKeyFilterTest {

  private static final String CHAVE = "chave-interna-secreta";

  private InternalApiKeyFilter filter;
  private FilterChain chain;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    filter = new InternalApiKeyFilter(CHAVE);
    chain = mock(FilterChain.class);
    response = new MockHttpServletResponse();
  }

  private MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    return request;
  }

  @Test
  @DisplayName("path fora de /api/v1/internal/ passa direto, sem exigir header")
  void naoInterceptaPathNaoInterno() throws Exception {
    filter.doFilter(request("/api/v1/checkout/products"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("prefixo exige a barra final: /api/v1/internalxyz nao e rota interna")
  void prefixoExigeBarraFinal() throws Exception {
    filter.doFilter(request("/api/v1/internalxyz"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("rota interna sem o header X-Internal-Api-Key responde 401 e nao segue a cadeia")
  void headerAusenteResulta401() throws Exception {
    filter.doFilter(request("/api/v1/internal/plans/todos"), response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"error\":\"Header X-Internal-Api-Key obrigatorio para endpoints internos\"}");
  }

  @Test
  @DisplayName("header em branco e tratado como ausente")
  void headerEmBrancoResulta401() throws Exception {
    MockHttpServletRequest request = request("/api/v1/internal/plans/venda-interna");
    request.addHeader("X-Internal-Api-Key", "   ");

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString())
        .contains("Header X-Internal-Api-Key obrigatorio para endpoints internos");
  }

  @Test
  @DisplayName("chave errada responde 401 com a mensagem de chave invalida")
  void chaveInvalidaResulta401() throws Exception {
    MockHttpServletRequest request = request("/api/v1/internal/plans/todos");
    request.addHeader("X-Internal-Api-Key", "chave-errada");

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Chave interna invalida\"}");
  }

  @Test
  @DisplayName("prefixo da chave correta nao passa (comparacao e do valor inteiro)")
  void prefixoDaChaveNaoPassa() throws Exception {
    MockHttpServletRequest request = request("/api/v1/internal/plans/todos");
    request.addHeader("X-Internal-Api-Key", CHAVE.substring(0, CHAVE.length() - 1));

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName("chave correta segue a cadeia")
  void chaveCorretaPassa() throws Exception {
    MockHttpServletRequest request = request("/api/v1/internal/plans/todos");
    request.addHeader("X-Internal-Api-Key", CHAVE);

    filter.doFilter(request, response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("chave correta com espacos em volta passa (trim(), como no original)")
  void chaveComEspacosPassa() throws Exception {
    MockHttpServletRequest request = request("/api/v1/internal/plans/todos");
    request.addHeader("X-Internal-Api-Key", "  " + CHAVE + "  ");

    filter.doFilter(request, response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("context path e descontado antes da comparacao de prefixo")
  void descontaContextPath() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/backend/api/v1/internal/plans/todos");
    request.setRequestURI("/backend/api/v1/internal/plans/todos");
    request.setContextPath("/backend");

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(401);
  }
}
