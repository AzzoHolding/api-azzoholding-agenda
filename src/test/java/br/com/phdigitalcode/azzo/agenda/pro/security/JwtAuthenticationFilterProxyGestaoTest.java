package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

/**
 * Incidente de 17/10/2026: toda tela da Gestao que passa pelo proxy administrativo voltava 401 e
 * mandava para o login. O SEG-016 passou a tratar usuario inexistente como revogado, e o subject do
 * token do proxy e um usuario da Gestao, que nao existe aqui.
 */
class JwtAuthenticationFilterProxyGestaoTest {

  private JwtService jwtService;
  private TokenRevocationService revogacao;
  private JwtAuthenticationFilter filtro;

  @BeforeEach
  void preparar() {
    jwtService = mock(JwtService.class);
    revogacao = mock(TokenRevocationService.class);
    filtro = new JwtAuthenticationFilter(jwtService, revogacao);
    // Usuario inexistente aqui: a revogacao diz "revogado" (SEG-016).
    when(revogacao.isRevoked(any(), anyLong())).thenReturn(true);
    when(jwtService.extractRoles(any())).thenReturn(Set.of("ADMIN"));
  }

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
  }

  private void chamarCom(Claims claims) throws Exception {
    when(jwtService.parseAndValidate("tk")).thenReturn(claims);
    MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/api/v1/admin/system/commercial/overview");
    pedido.addHeader("Authorization", "Bearer tk");
    filtro.doFilter(pedido, new MockHttpServletResponse(), new MockFilterChain());
  }

  private static Claims claims(String origem) {
    var b = Jwts.claims()
        .subject(UUID.randomUUID().toString())
        .issuedAt(new Date())
        .add("groups", List.of("ADMIN"));
    if (origem != null) b.add("origem", origem);
    return b.build();
  }

  @Test
  void tokenDoProxyDaGestaoAutenticaMesmoSemUsuarioAqui() throws Exception {
    chamarCom(claims(JwtAuthenticationFilter.ORIGEM_PROXY_GESTAO));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    verify(revogacao, never()).isRevoked(any(), anyLong());
  }

  @Test
  void tokenComumDeUsuarioInexistenteContinuaRevogado() throws Exception {
    chamarCom(claims(null));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void outraOrigemNaoPulaARevogacao() throws Exception {
    chamarCom(claims("qualquer-outra"));

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
