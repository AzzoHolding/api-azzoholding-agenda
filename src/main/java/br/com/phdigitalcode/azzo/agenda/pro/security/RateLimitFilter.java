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
  /** Regras por padrao (rotas com variavel no caminho, como o slug do salao). */
  private final Map<java.util.regex.Pattern, RateLimitRule> rulesByPattern;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public RateLimitFilter(Map<String, RateLimitRule> rulesByPath) {
    this(rulesByPath, Map.of());
  }

  public RateLimitFilter(
      Map<String, RateLimitRule> rulesByPath, Map<java.util.regex.Pattern, RateLimitRule> rulesByPattern) {
    this.rulesByPath = rulesByPath;
    this.rulesByPattern = rulesByPattern;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    String caminho = CaminhoDaRequisicao.normalizado(request); // SEG-002: "/auth/%6Cogin" tambem conta
    RateLimitRule rule = rulesByPath.get(caminho);
    if (rule == null) {
      for (Map.Entry<java.util.regex.Pattern, RateLimitRule> e : rulesByPattern.entrySet()) {
        if (e.getKey().matcher(caminho).matches()) {
          rule = e.getValue();
          break;
        }
      }
    }
    if (rule == null) {
      chain.doFilter(request, response);
      return;
    }

    String identity = resolveClientIp(request);
    RateLimitRule regra = rule;
    Bucket bucket = buckets.computeIfAbsent(regra.bucketName() + ":" + identity, k -> newBucket(regra));
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
        @Value("${app.rate-limit.auth-reset-password.window-minutes:10}") int resetWindow,
        @Value("${app.rate-limit.public-booking.max-attempts:10}") int bookingMax,
        @Value("${app.rate-limit.public-booking.window-minutes:10}") int bookingWindow,
        @Value("${app.rate-limit.public-funnel.max-attempts:60}") int funnelMax,
        @Value("${app.rate-limit.public-funnel.window-minutes:10}") int funnelWindow) {

      Map<String, RateLimitRule> rules = Map.of(
          "/api/v1/auth/login",
          new RateLimitRule("auth-login", loginMax, Duration.ofMinutes(loginWindow)),
          "/api/v1/auth/register",
          new RateLimitRule("auth-register", registerMax, Duration.ofMinutes(registerWindow)),
          "/api/v1/auth/forgot-password",
          new RateLimitRule("auth-forgot-password", forgotMax, Duration.ofMinutes(forgotWindow)),
          "/api/v1/auth/reset-password",
          new RateLimitRule("auth-reset-password", resetMax, Duration.ofMinutes(resetWindow)),
          "/api/v1/auth/confirm-email",
          new RateLimitRule("auth-confirm-email", resetMax, Duration.ofMinutes(resetWindow)),
          "/api/v1/auth/resend-email-confirmation",
          new RateLimitRule("auth-resend-email-confirmation", forgotMax, Duration.ofMinutes(forgotWindow)));

      // Pentest 14/10/2026 (SEG-011/AGD-012): o agendamento publico nao tinha limite — dava para
      // encher a agenda de um salao de pedidos falsos e disparar confirmacao para telefones de
      // terceiros. O slug esta no meio do caminho, por isso regra por padrao.
      Map<java.util.regex.Pattern, RateLimitRule> porPadrao = Map.of(
          java.util.regex.Pattern.compile("/api/v1/public/salons/[^/]+/appointments"),
          new RateLimitRule("public-booking", bookingMax, Duration.ofMinutes(bookingWindow)),
          java.util.regex.Pattern.compile("/api/v1/public/salons/[^/]+/booking-funnel/events"),
          new RateLimitRule("public-funnel", funnelMax, Duration.ofMinutes(funnelWindow)));
      FilterRegistrationBean<RateLimitFilter> registration =
          new FilterRegistrationBean<>(new RateLimitFilter(rules, porPadrao));
      registration.addUrlPatterns("/api/v1/auth/*", "/api/v1/public/*");
      registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
      return registration;
    }
  }
}
