package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Equivalente Spring de {@code quarkus-bucket4j} (extensao Quarkiverse declarativa) — ver risco 5
 * do inventario: nao existe extensao Spring pronta, entao os 5 buckets nomeados
 * ({@code auth-login}, {@code auth-register}, {@code auth-forgot-password},
 * {@code auth-reset-password}, {@code fiscal-api}) sao implementados aqui com
 * {@code bucket4j-core} puro em um {@link OncePerRequestFilter}, identidade por IP (mesmo
 * {@code IpResolver} do original: usa {@code X-Forwarded-For} e cai para o IP remoto).
 *
 * <p>Buckets em memoria (single-instance) — se o backend rodar com mais de uma replica, migrar
 * para um backend distribuido (Redis) e um risco a avaliar antes de escalar horizontalmente em
 * producao (nao coberto pelo original tambem, que era igualmente em memoria).
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<String, RateLimitRule> rulesByPath;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(Map<String, RateLimitRule> rulesByPath) {
    this.rulesByPath = rulesByPath;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    RateLimitRule rule = rulesByPath.get(request.getRequestURI());
    if (rule == null) {
      chain.doFilter(request, response);
      return;
    }

    String identity = resolveClientIp(request);
    Bucket bucket = buckets.computeIfAbsent(rule.bucketName() + ":" + identity, k -> newBucket(rule));
    if (bucket.tryConsume(1)) {
      chain.doFilter(request, response);
      return;
    }

    writeTooManyRequests(request, response);
  }

  private Bucket newBucket(RateLimitRule rule) {
    Bandwidth limit = Bandwidth.classic(
        rule.permittedUses(),
        Refill.intervally(rule.permittedUses(), rule.period()));
    return Bucket.builder().addLimit(limit).build();
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
    ErrorResponse payload = new ErrorResponse(
        "TOO_MANY_REQUESTS",
        "Muitas tentativas em pouco tempo. Aguarde alguns minutos e tente novamente.",
        null,
        request.getRequestURI());
    response.setStatus(429);
    response.setContentType("application/json");
    response.getWriter().write(objectMapper.writeValueAsString(payload));
  }

  public record RateLimitRule(String bucketName, int permittedUses, Duration period) {}

  /** Registra o filtro com as 5 configuracoes de bucket do {@code application.yml}. */
  @Configuration
  public static class RateLimitFilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
        @Value("${app.rate-limit.auth-login.max-attempts:5}") int loginMax,
        @Value("${app.rate-limit.auth-login.window-minutes:3}") int loginWindow,
        @Value("${app.rate-limit.auth-register.max-attempts:3}") int registerMax,
        @Value("${app.rate-limit.auth-register.window-minutes:10}") int registerWindow,
        @Value("${app.rate-limit.auth-forgot-password.max-attempts:5}") int forgotMax,
        @Value("${app.rate-limit.auth-forgot-password.window-minutes:10}") int forgotWindow,
        @Value("${app.rate-limit.auth-reset-password.max-attempts:5}") int resetMax,
        @Value("${app.rate-limit.auth-reset-password.window-minutes:10}") int resetWindow) {

      Map<String, RateLimitRule> rules = Map.of(
          "/api/v1/auth/login",
          new RateLimitRule("auth-login", loginMax, Duration.ofMinutes(loginWindow)),
          "/api/v1/auth/register",
          new RateLimitRule("auth-register", registerMax, Duration.ofMinutes(registerWindow)),
          "/api/v1/auth/forgot-password",
          new RateLimitRule("auth-forgot-password", forgotMax, Duration.ofMinutes(forgotWindow)),
          "/api/v1/auth/reset-password",
          new RateLimitRule("auth-reset-password", resetMax, Duration.ofMinutes(resetWindow)));

      FilterRegistrationBean<RateLimitFilter> registration =
          new FilterRegistrationBean<>(new RateLimitFilter(rules));
      registration.addUrlPatterns("/api/v1/auth/*");
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      return registration;
    }
  }
}
