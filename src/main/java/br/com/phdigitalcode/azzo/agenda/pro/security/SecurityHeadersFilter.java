package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Porte verbatim de {@code modules/security/infrastructure/SecurityHeadersResponseFilter.java}. */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private static final String X_FRAME_OPTIONS = "X-Frame-Options";
  private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  private static final String REFERRER_POLICY = "Referrer-Policy";
  private static final String PERMISSIONS_POLICY = "Permissions-Policy";

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    putIfAbsent(response, X_FRAME_OPTIONS, "DENY");
    putIfAbsent(response, X_CONTENT_TYPE_OPTIONS, "nosniff");
    putIfAbsent(response, REFERRER_POLICY, "no-referrer");
    putIfAbsent(response, PERMISSIONS_POLICY, "geolocation=(), camera=(), microphone=()");
    filterChain.doFilter(request, response);
  }

  private void putIfAbsent(HttpServletResponse response, String header, String value) {
    if (!response.containsHeader(header)) {
      response.setHeader(header, value);
    }
  }
}
