package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Porte de {@code modules/security/infrastructure/InternalApiKeyFilter.java}.
 *
 * <p>Autenticacao dos endpoints internos ({@code /api/v1/internal/}), usados na comunicacao
 * service-to-service (assistant API / api-gerenciamento -> backend principal). Valida o header
 * {@code X-Internal-Api-Key} contra {@code app.internal.api-key} em tempo constante.
 *
 * <p>Estes paths continuam em {@code permitAll} no {@code SecurityConfig} (nao ha JWT nessas
 * chamadas, exatamente como no original, onde {@code quarkus.http.auth.permission.public.paths}
 * inclui {@code /api/v1/internal/*}) — quem autentica e este filtro.
 *
 * <p>O original le o path via {@code UriInfo.getPath()}, que no RESTEasy pode ou nao vir com a
 * barra inicial (o {@code LicenseFilter} do original normaliza justamente por isso). Aqui o path
 * vem de {@code HttpServletRequest.getRequestURI()}, que sempre inclui a barra inicial e o
 * context path — dai o context path ser descontado antes da comparacao de prefixo.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(InternalApiKeyFilter.class);
  private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal/";
  private static final String HEADER_NAME = "X-Internal-Api-Key";

  private final String configuredApiKey;

  public InternalApiKeyFilter(@Value("${app.internal.api-key:changeme-dev}") String configuredApiKey) {
    this.configuredApiKey = configuredApiKey;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = resolvePath(request);
    if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    String providedKey = request.getHeader(HEADER_NAME);
    if (providedKey == null || providedKey.isBlank()) {
      LOG.warn("Requisicao interna rejeitada: header {} ausente. Path: {}", HEADER_NAME, path);
      reject(response, "Header " + HEADER_NAME + " obrigatorio para endpoints internos");
      return;
    }

    if (!constantTimeEquals(configuredApiKey, providedKey.trim())) {
      LOG.warn("Requisicao interna rejeitada: chave invalida. Path: {}", path);
      reject(response, "Chave interna invalida");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static String resolvePath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      uri = uri.substring(contextPath.length());
    }
    return uri.startsWith("/") ? uri : "/" + uri;
  }

  private static void reject(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
    response.getWriter().flush();
  }

  private static boolean constantTimeEquals(String expected, String provided) {
    if (expected == null || provided == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        provided.getBytes(StandardCharsets.UTF_8));
  }
}
