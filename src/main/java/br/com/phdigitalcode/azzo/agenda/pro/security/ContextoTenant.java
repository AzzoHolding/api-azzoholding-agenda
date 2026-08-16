package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.UUID;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Equivalente Spring de {@code infrastructure/tenant/ContextoTenant.java} (bean
 * {@code @RequestScoped} no Quarkus). Le o tenant do claim {@code tenant_id}/{@code tid} do JWT
 * autenticado (via {@link JwtPrincipal} no {@code SecurityContext}), com fallback para o header
 * {@code X-Tenant-Id} (usado apenas no fluxo sistema-a-sistema do assistant, ver inventario secao
 * 6.6). Falha explicitamente ({@link IllegalStateException}) se o tenant estiver ausente — sem
 * default silencioso, mesmo comportamento do original.
 *
 * <p>{@code proxyMode = TARGET_CLASS} e necessario porque este bean de escopo de requisicao e
 * injetado em beans singleton (ex.: {@code GlobalExceptionHandler}, {@code PermissionService}) —
 * nota de risco 10 do inventario.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ContextoTenant {

  private UUID tenantIdOverride;

  public void definirTenantId(UUID tenantId) {
    this.tenantIdOverride = tenantId;
  }

  public void limparTenantIdOverride() {
    this.tenantIdOverride = null;
  }

  public UUID obterTenantIdOuFalhar() {
    if (tenantIdOverride != null) {
      return tenantIdOverride;
    }

    UUID fromJwt = obterTenantIdViaJwt();
    if (fromJwt != null) {
      return fromJwt;
    }

    String tid = obterTenantIdViaHeader();
    if (tid == null || tid.isBlank()) {
      throw new IllegalStateException("TenantId ausente (claim tenant_id/tid no JWT ou header X-Tenant-Id)");
    }
    return UUID.fromString(tid);
  }

  private UUID obterTenantIdViaJwt() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
      return jwtPrincipal.tenantId();
    }
    return null;
  }

  private String obterTenantIdViaHeader() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) return null;
    HttpServletRequest request = attributes.getRequest();
    String value = request.getHeader("X-Tenant-Id");
    if (value == null || value.isBlank()) {
      value = request.getHeader("x-tenant-id");
    }
    return value;
  }
}
