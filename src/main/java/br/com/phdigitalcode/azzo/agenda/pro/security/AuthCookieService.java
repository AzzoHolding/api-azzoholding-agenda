package br.com.phdigitalcode.azzo.agenda.pro.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Equivalente Spring de {@code modules/auth/infrastructure/security/AuthCookieService.java}.
 * Constroi os cookies httpOnly {@code AZZO_ACCESS_TOKEN} (path {@code /}) e
 * {@code AZZO_REFRESH_TOKEN} (path {@code /api/v1/auth}), preservando exatamente nomes, paths,
 * flags e a validacao de startup (SameSite=None exige Secure=true).
 */
@Service
public class AuthCookieService {

  public static final String ACCESS_TOKEN_COOKIE = "AZZO_ACCESS_TOKEN";
  public static final String REFRESH_TOKEN_COOKIE = "AZZO_REFRESH_TOKEN";

  @Value("${app.auth.cookie.secure:true}")
  private boolean secureCookie;

  @Value("${app.auth.cookie.same-site:STRICT}")
  private String sameSitePolicy;

  @Value("${app.auth.cookie.domain:__unset__}")
  private String cookieDomain;

  @PostConstruct
  void validateSecurityCookiePolicy() {
    if ("None".equalsIgnoreCase(resolveSameSitePolicy()) && !secureCookie) {
      throw new IllegalStateException(
          "Configuracao invalida: SameSite=None exige app.auth.cookie.secure=true");
    }
  }

  public ResponseCookie buildAccessTokenCookie(String token, long maxAgeSeconds) {
    return baseBuilder(ACCESS_TOKEN_COOKIE, token, "/", maxAgeSeconds).build();
  }

  public ResponseCookie buildRefreshTokenCookie(String token, long maxAgeSeconds) {
    return baseBuilder(REFRESH_TOKEN_COOKIE, token, "/api/v1/auth", maxAgeSeconds).build();
  }

  public ResponseCookie clearAccessTokenCookie() {
    return baseBuilder(ACCESS_TOKEN_COOKIE, "", "/", 0).build();
  }

  public ResponseCookie clearRefreshTokenCookie() {
    return baseBuilder(REFRESH_TOKEN_COOKIE, "", "/api/v1/auth", 0).build();
  }

  private ResponseCookie.ResponseCookieBuilder baseBuilder(
      String name, String value, String path, long maxAgeSeconds) {
    ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secureCookie)
        .path(path)
        .sameSite(resolveSameSitePolicy())
        .maxAge(maxAgeSeconds);
    String domain = cookieDomain == null ? "" : cookieDomain.trim();
    if (!domain.isBlank() && !"__unset__".equalsIgnoreCase(domain)) {
      builder.domain(domain);
    }
    return builder;
  }

  private String resolveSameSitePolicy() {
    if (sameSitePolicy == null || sameSitePolicy.isBlank()) return "Strict";
    return switch (sameSitePolicy.trim().toUpperCase()) {
      case "NONE" -> "None";
      case "LAX" -> "Lax";
      default -> "Strict";
    };
  }
}
