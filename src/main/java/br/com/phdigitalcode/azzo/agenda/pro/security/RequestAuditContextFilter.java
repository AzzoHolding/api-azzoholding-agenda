package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Equivalente Spring de {@code modules/audit/infrastructure/RequestAuditContextFilter.java}
 * ({@code @Provider @Priority(Priorities.AUTHENTICATION - 10)} no Quarkus — roda ANTES da
 * autenticacao). Registrado em {@code SecurityConfig} com
 * {@code addFilterBefore(..., JwtAuthenticationFilter.class)} para preservar essa ordem.
 */
@Component
public class RequestAuditContextFilter extends OncePerRequestFilter {

  private final RequestAuditContext requestAuditContext;

  public RequestAuditContextFilter(RequestAuditContext requestAuditContext) {
    this.requestAuditContext = requestAuditContext;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    requestAuditContext.setRequestId(resolveRequestId(request));
    requestAuditContext.setIpAddress(resolveIpAddress(request));
    requestAuditContext.setUserAgent(request.getHeader("User-Agent"));
    filterChain.doFilter(request, response);
  }

  private String resolveRequestId(HttpServletRequest request) {
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) {
      requestId = request.getHeader("x-request-id");
    }
    return requestId;
  }

  private String resolveIpAddress(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor == null || forwardedFor.isBlank()) {
      forwardedFor = request.getHeader("x-forwarded-for");
    }
    String firstForwarded = firstIp(forwardedFor);
    if (firstForwarded != null) return firstForwarded;

    String realIp = request.getHeader("X-Real-IP");
    if (realIp == null || realIp.isBlank()) {
      realIp = request.getHeader("x-real-ip");
    }
    if (realIp != null && !realIp.isBlank()) return realIp.trim();

    String forwarded = request.getHeader("Forwarded");
    if (forwarded != null && !forwarded.isBlank()) {
      String parsed = parseForwardedFor(forwarded);
      if (parsed != null) return parsed;
    }

    // Fallback: IP direto da conexão TCP (sem proxy).
    String remoteAddr = request.getRemoteAddr();
    return (remoteAddr == null || remoteAddr.isBlank()) ? null : remoteAddr;
  }

  private String firstIp(String forwardedFor) {
    if (forwardedFor == null || forwardedFor.isBlank()) return null;
    String[] values = forwardedFor.split(",");
    if (values.length == 0) return null;
    String first = values[0] != null ? values[0].trim() : null;
    return (first == null || first.isBlank()) ? null : first;
  }

  private String parseForwardedFor(String forwarded) {
    String[] parts = forwarded.split(";");
    for (String part : parts) {
      String trimmed = part != null ? part.trim() : "";
      if (!trimmed.toLowerCase().startsWith("for=")) continue;
      String value = trimmed.substring(4).trim();
      if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
        value = value.substring(1, value.length() - 1);
      }
      int bracket = value.indexOf(']');
      if (value.startsWith("[") && bracket > 0) {
        value = value.substring(1, bracket);
      } else {
        int colon = value.indexOf(':');
        if (colon > 0 && value.indexOf(':', colon + 1) == -1) {
          value = value.substring(0, colon);
        }
      }
      if (!value.isBlank()) return value;
    }
    return null;
  }
}
