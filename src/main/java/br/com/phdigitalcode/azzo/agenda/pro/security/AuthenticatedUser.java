package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Equivalente Spring do {@code JsonWebToken} injetado nos services do Quarkus: expoe o
 * {@code sub} (userId) e os {@code groups} (roles) do usuario autenticado.
 *
 * <p>No Quarkus cada service injetava {@code JsonWebToken} e repetia {@code jwt.getSubject()} /
 * {@code jwt.getGroups()}. Aqui isso vira um bean unico injetado por construtor, com a mesma
 * semantica dos originais: {@link #idOuNulo()} nunca lanca (o original envolvia em try/catch para
 * requisicoes sem token) e {@link #idOuFalhar()} lanca {@link IllegalStateException} — mesma
 * excecao do {@code obterUserIdOuFalhar} original.
 */
@Component
public class AuthenticatedUser {

  /** Igual a {@code obterUserIdOptional()} dos services originais: null quando nao ha token. */
  public UUID idOuNulo() {
    JwtPrincipal principal = principalOuNulo();
    return principal != null ? principal.userId() : null;
  }

  /** Igual a {@code obterUserIdOuFalhar()} original. */
  public UUID idOuFalhar() {
    UUID userId = idOuNulo();
    if (userId == null) {
      throw new IllegalStateException("UserId ausente no JWT");
    }
    return userId;
  }

  /**
   * Igual a {@code jwt.getClaim("tenant_id")} original (tratado com try/catch e devolvendo null
   * em qualquer falha) — usado por {@code SystemAdminService} para auditoria de acoes globais de
   * ADMIN, cujo JWT tambem carrega um {@code tenant_id} (o tenant "casa" do operador).
   */
  public java.util.UUID tenantIdOuNulo() {
    JwtPrincipal principal = principalOuNulo();
    return principal != null ? principal.tenantId() : null;
  }

  /**
   * Igual a {@code resolveAuthenticatedUserName()} original ({@code jwt.getClaim("name")} com
   * fallback para {@code jwt.getName()}, e "ADMIN" se nada disponivel) — usado por
   * {@code SystemAdminService} ao registrar quem respondeu uma sugestao.
   */
  public String nomeOuAdmin() {
    JwtPrincipal principal = principalOuNulo();
    if (principal != null && principal.name() != null && !principal.name().isBlank()) {
      return principal.name();
    }
    return "ADMIN";
  }

  /** Igual a {@code obterRoleOptional()} original: primeira role do token, ou null. */
  public String roleOuNulo() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) return null;
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .findFirst()
        .orElse(null);
  }

  /** Igual a {@code isProfessional()} original ({@code jwt.getGroups().contains("PROFESSIONAL")}). */
  public boolean temRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) return false;
    String expected = "ROLE_" + role;
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(expected::equals);
  }

  public boolean isProfessional() {
    return temRole("PROFESSIONAL");
  }

  private JwtPrincipal principalOuNulo() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal) {
      return jwtPrincipal;
    }
    return null;
  }
}
