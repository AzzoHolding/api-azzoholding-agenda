package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Cobre {@code tenantIdOuNulo()}/{@code nomeOuAdmin()} (adicionados para {@code SystemAdminService},
 * espelham {@code jwt.getClaim("tenant_id")}/{@code resolveAuthenticatedUserName()} do original).
 */
class AuthenticatedUserTest {

  private final AuthenticatedUser authenticatedUser = new AuthenticatedUser();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAs(JwtPrincipal principal) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @Test
  void tenantIdOuNuloRetornaNuloSemAutenticacao() {
    assertThat(authenticatedUser.tenantIdOuNulo()).isNull();
  }

  @Test
  void tenantIdOuNuloRetornaTenantIdDoPrincipal() {
    UUID tenantId = UUID.randomUUID();
    authenticateAs(new JwtPrincipal(UUID.randomUUID(), tenantId, "a@a.com", "Fulano", 0L));

    assertThat(authenticatedUser.tenantIdOuNulo()).isEqualTo(tenantId);
  }

  @Test
  void nomeOuAdminRetornaAdminSemAutenticacao() {
    assertThat(authenticatedUser.nomeOuAdmin()).isEqualTo("ADMIN");
  }

  @Test
  void nomeOuAdminRetornaNomeDoPrincipalQuandoPresente() {
    authenticateAs(new JwtPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@a.com", "Fulano de Tal", 0L));

    assertThat(authenticatedUser.nomeOuAdmin()).isEqualTo("Fulano de Tal");
  }

  @Test
  void nomeOuAdminRetornaAdminQuandoNomeEmBranco() {
    authenticateAs(new JwtPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a@a.com", "  ", 0L));

    assertThat(authenticatedUser.nomeOuAdmin()).isEqualTo("ADMIN");
  }
}
