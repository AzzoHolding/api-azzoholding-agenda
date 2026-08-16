package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro unico que substitui, em conjunto:
 * <ul>
 *   <li>{@code CookieJwtBridgeFilter} (prioridade AUTHENTICATION no Quarkus): le o cookie
 *       {@code AZZO_ACCESS_TOKEN} e usa como Bearer token somente se o header
 *       {@code Authorization} nao estiver presente (mesmo fallback exato do original — risco 6 do
 *       inventario);</li>
 *   <li>validacao {@code quarkus-smallrye-jwt} (assinatura RSA + expiracao + issuer);</li>
 *   <li>{@code TokenRevocationAugmentor} (rejeita token emitido antes de
 *       {@code tokensRevokedBefore}).</li>
 * </ul>
 *
 * <p>Posicionado antes do filtro padrao de autorizacao do Spring Security. Nao lanca excecao em
 * token ausente/invalido (deixa o {@code SecurityContext} vazio — a decisao de autorizar ou nao
 * fica com {@code authorizeHttpRequests}, mesma semantica do
 * {@code quarkus.http.auth.permission.*}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  static final String ACCESS_TOKEN_COOKIE = "AZZO_ACCESS_TOKEN";

  private final JwtService jwtService;
  private final TokenRevocationService tokenRevocationService;

  public JwtAuthenticationFilter(JwtService jwtService, TokenRevocationService tokenRevocationService) {
    this.jwtService = jwtService;
    this.tokenRevocationService = tokenRevocationService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null && !token.isBlank()) {
      try {
        Claims claims = jwtService.parseAndValidate(token);
        UUID userId = UUID.fromString(claims.getSubject());
        UUID tenantId = jwtService.extractTenantId(claims);
        Long iat = claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant().getEpochSecond() : null;

        if (iat != null && tokenRevocationService.isRevoked(userId, iat)) {
          LOG.warn("JWT rejeitado: token revogado (userId={})", userId);
        } else {
          Set<String> roles = jwtService.extractRoles(claims);
          JwtPrincipal principal = new JwtPrincipal(
              userId,
              tenantId,
              claims.get("upn", String.class),
              claims.get("name", String.class),
              iat != null ? iat : 0L);
          List<GrantedAuthority> authorities = roles.stream()
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .collect(Collectors.toList());
          SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(principal, authorities));
        }
      } catch (JwtException | IllegalArgumentException ex) {
        LOG.debug("JWT invalido/expirado: {}", ex.getMessage());
      }
    }
    filterChain.doFilter(request, response);
  }

  private String resolveToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && !authorization.isBlank()) {
      if (authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
        return authorization.substring(7).trim();
      }
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies) {
      if (ACCESS_TOKEN_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
        return cookie.getValue();
      }
    }
    return null;
  }

  /** Marker simples para diferenciar de outros tipos de Authentication no SecurityContext. */
  static final class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final JwtPrincipal principal;

    JwtAuthenticationToken(JwtPrincipal principal, List<GrantedAuthority> authorities) {
      super(authorities);
      this.principal = principal;
      setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getPrincipal() {
      return principal;
    }

    @Override
    public String getName() {
      return principal.userId() != null ? principal.userId().toString() : null;
    }
  }
}
