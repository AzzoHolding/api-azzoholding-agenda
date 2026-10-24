package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ErrorResponse;
import br.com.phdigitalcode.azzo.agenda.pro.service.LicenseStatusService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Porte de {@code modules/security/infrastructure/LicenseFilter.java}.
 *
 * <p><b>Achado em 28/09/2026, investigando "o que acontece quando o plano vence": este filtro
 * nunca tinha sido portado.</b> O original bloqueava, com {@code 402 PLAN_EXPIRED}, qualquer rota
 * autenticada nao-ADMIN de um tenant com {@link LicenseStatusService#deveBloquear}. Sem ele, um
 * tenant inadimplente continuava com acesso total ao sistema — o unico efeito real de um plano
 * vencido era o WhatsApp automatico parar (`WhatsAppWebhookController`, "Fix 4", que so existe
 * porque era uma excecao a ESTE filtro geral no original, portada sem o filtro que ela excecionava).
 *
 * <p>Mesma allowlist do original — login/registro/refresh, catalogo publico, perfil do salao,
 * menus, "quem sou eu" e o proprio modulo de billing (senao ninguem consegue pagar ou ver a
 * propria licenca vencida). {@code /webhook/*} continua isento aqui tambem: o webhook nao tem JWT
 * para este filtro avaliar, e quem o barra e a checagem propria dentro do controller.
 */
@Component
public class LicenseFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(LicenseFilter.class);

  private static final Set<String> AUTH_WHITELIST = Set.of(
      "/api/v1/auth/login",
      "/api/v1/auth/register",
      "/api/v1/auth/refresh",
      "/api/v1/checkout/products",
      "/api/v1/salon/profile",
      "/api/v1/config/menus/current",
      "/api/v1/auth/me",
      "/api/v1/billing/subscriptions/current",
      "/api/v1/billing/subscriptions",
      "/api/v1/billing/payments");

  private final LicenseStatusService licenseStatusService;
  private final ContextoTenant contextoTenant;
  private final ObjectMapper objectMapper;

  public LicenseFilter(
      LicenseStatusService licenseStatusService,
      ContextoTenant contextoTenant,
      ObjectMapper objectMapper) {
    this.licenseStatusService = licenseStatusService;
    this.contextoTenant = contextoTenant;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = resolvePath(request);
    if (isAllowedRoute(path)) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof JwtPrincipal)) {
      // Sem JWT: visitante anonimo ou chamada service-to-service (X-Internal-Api-Key), nenhum
      // dos dois tem tenant para esta checagem avaliar — mesma exclusao implicita do original
      // (SecurityIdentity.isAnonymous()).
      filterChain.doFilter(request, response);
      return;
    }
    if (temRoleAdmin(authentication)) {
      filterChain.doFilter(request, response);
      return;
    }

    UUID tenantId;
    try {
      tenantId = contextoTenant.obterTenantIdOuFalhar();
    } catch (RuntimeException ignored) {
      filterChain.doFilter(request, response);
      return;
    }

    if (licenseStatusService.deveBloquear(tenantId)) {
      LOG.warn(
          "license.access.blocked {}",
          CorrelatedLogging.context(
              "method", request.getMethod(),
              "path", path,
              "tenantId", tenantId));
      escreverBloqueio(response, path);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean isAllowedRoute(String path) {
    if (path.startsWith("/webhook/")) return true;
    return AUTH_WHITELIST.contains(path);
  }

  private boolean temRoleAdmin(Authentication authentication) {
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if ("ROLE_ADMIN".equals(authority.getAuthority())) return true;
    }
    return false;
  }

  private void escreverBloqueio(HttpServletResponse response, String path) throws IOException {
    ErrorResponse payload = new ErrorResponse(
        "PLAN_EXPIRED",
        "Seu plano esta vencido. Regularize o pagamento para continuar utilizando o sistema.",
        null,
        path);
    response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(payload));
  }

  /** Igual a {@code InternalApiKeyFilter.resolvePath}: descontar o context path, sempre com barra. */
  private static String resolvePath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      uri = uri.substring(contextPath.length());
    }
    return uri.startsWith("/") ? uri : "/" + uri;
  }
}
