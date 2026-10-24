package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.service.LicenseStatusService;
import jakarta.servlet.FilterChain;

/**
 * Trava o achado de 28/09/2026: este filtro nunca tinha sido portado do Quarkus original, e sem
 * ele um tenant com o plano vencido continuava com acesso total ao sistema (so o WhatsApp
 * automatico parava). Cobre a MESMA allowlist e as MESMAS excecoes do original
 * ({@code LicenseFilter.java} em azzo-agenda-pro).
 */
class LicenseFilterTest {

  private LicenseStatusService licenseStatusService;
  private ContextoTenant contextoTenant;
  private LicenseFilter filter;
  private FilterChain chain;
  private MockHttpServletResponse response;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    licenseStatusService = mock(LicenseStatusService.class);
    contextoTenant = mock(ContextoTenant.class);
    filter = new LicenseFilter(licenseStatusService, contextoTenant, new ObjectMapper());
    chain = mock(FilterChain.class);
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void limpar() {
    SecurityContextHolder.clearContext();
  }

  private MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
    request.setRequestURI(uri);
    return request;
  }

  private void autenticarComoTenant(UUID tenant, String... roles) {
    JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), tenant, "dona@salao.com", "Dona", 0L);
    List<GrantedAuthority> authorities = java.util.Arrays.stream(roles)
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationFilter.JwtAuthenticationToken(principal, authorities));
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenant);
  }

  @Test
  @DisplayName("sem autenticacao (visitante/chamada interna) passa direto, mesmo com plano vencido")
  void semAutenticacaoPassaDireto() throws Exception {
    when(licenseStatusService.deveBloquear(any())).thenReturn(true);

    filter.doFilter(request("/api/v1/agendamentos"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("/webhook/* nunca e bloqueado por este filtro (quem barra e o proprio controller)")
  void webhookSempreLivre() throws Exception {
    autenticarComoTenant(tenantId, "OWNER");
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(true);

    filter.doFilter(request("/webhook/whatsapp"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    verify(licenseStatusService, never()).deveBloquear(any());
  }

  @Test
  @DisplayName("rotas da allowlist passam mesmo com plano vencido, para o salao conseguir pagar")
  void allowlistPassaComPlanoVencido() throws Exception {
    autenticarComoTenant(tenantId, "OWNER");
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(true);

    for (String rota : List.of(
        "/api/v1/auth/me",
        "/api/v1/checkout/products",
        "/api/v1/salon/profile",
        "/api/v1/config/menus/current",
        "/api/v1/billing/subscriptions/current",
        "/api/v1/billing/subscriptions",
        "/api/v1/billing/payments")) {
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilter(request(rota), resp, chain);
      assertThat(resp.getStatus()).as("rota %s deveria passar", rota).isEqualTo(200);
    }
  }

  @Test
  @DisplayName("ADMIN atravessa o bloqueio de licenca, mesmo plano vencido")
  void adminAtravessaBloqueio() throws Exception {
    autenticarComoTenant(tenantId, "ADMIN");
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(true);

    filter.doFilter(request("/api/v1/agendamentos"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    verify(licenseStatusService, never()).deveBloquear(any());
  }

  @Test
  @DisplayName("plano em dia: segue a cadeia normalmente")
  void planoEmDiaPassa() throws Exception {
    autenticarComoTenant(tenantId, "OWNER");
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(false);

    filter.doFilter(request("/api/v1/agendamentos"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("plano vencido numa rota comum: 402 PLAN_EXPIRED, sem seguir a cadeia")
  void planoVencidoBloqueiaRotaComum() throws Exception {
    autenticarComoTenant(tenantId, "OWNER");
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(true);

    filter.doFilter(request("/api/v1/agendamentos"), response, chain);

    verify(chain, never()).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(402);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString()).contains("\"code\":\"PLAN_EXPIRED\"");
  }

  @Test
  @DisplayName("sem tenant resolvivel (ContextoTenant falha): passa direto, nao bloqueia")
  void semTenantResolveNaoBloqueia() throws Exception {
    JwtPrincipal principal = new JwtPrincipal(UUID.randomUUID(), null, "dona@salao.com", "Dona", 0L);
    SecurityContextHolder.getContext().setAuthentication(
        new JwtAuthenticationFilter.JwtAuthenticationToken(
            principal, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    when(contextoTenant.obterTenantIdOuFalhar()).thenThrow(new IllegalStateException("TenantId ausente"));

    filter.doFilter(request("/api/v1/agendamentos"), response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    verify(licenseStatusService, never()).deveBloquear(any());
  }

  @Test
  @DisplayName("context path e descontado antes de comparar com a allowlist")
  void descontaContextPath() throws Exception {
    autenticarComoTenant(tenantId, "OWNER");
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(true);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/backend/api/v1/auth/me");
    request.setRequestURI("/backend/api/v1/auth/me");
    request.setContextPath("/backend");

    filter.doFilter(request, response, chain);

    verify(chain, times(1)).doFilter(any(), any());
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
