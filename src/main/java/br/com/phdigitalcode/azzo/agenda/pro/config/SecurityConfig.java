package br.com.phdigitalcode.azzo.agenda.pro.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.com.phdigitalcode.azzo.agenda.pro.security.InternalApiKeyFilter;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtAuthenticationFilter;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequestAuditContextFilter;
import br.com.phdigitalcode.azzo.agenda.pro.security.SecurityHeadersFilter;

/**
 * Equivalente Spring de {@code quarkus.http.auth.permission.*} + {@code quarkus.http.cors.*}.
 *
 * <p>{@code SecurityFilterChain} explicito com {@code @EnableMethodSecurity} — NUNCA
 * {@code WebSecurityConfigurerAdapter} (removido/deprecado), conforme exigido pelo escopo desta
 * migracao. RBAC grosso via {@code @PreAuthorize}/{@code hasRole} nos controllers (nao aqui,
 * espelhando {@code @RolesAllowed} por metodo no Quarkus original). RBAC fino via
 * {@code @RequiresPermission} + AOP (ver {@code RequiresPermissionAspect}).
 *
 * <p>Allowlist de rotas publicas replicada de
 * {@code quarkus.http.auth.permission.public.paths} (ver risco 7 do inventario). Rotas de modulos
 * ainda nao migrados ({@code /api/v1/public/*}, {@code /api/v1/checkout/*},
 * {@code /api/v1/internal/*}, {@code /api/v1/storage/proxy}, {@code /webhook/*}) sao mantidas na
 * allowlist por fidelidade ao contrato original, mesmo sem controller correspondente ainda.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final SecurityHeadersFilter securityHeadersFilter;
  private final InternalApiKeyFilter internalApiKeyFilter;
  private final RequestAuditContextFilter requestAuditContextFilter;

  @Value("${app.cors.origins:http://localhost:5173,http://127.0.0.1:5173,https://localhost:5173,https://127.0.0.1:5173}")
  private List<String> corsOrigins;

  public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
      SecurityHeadersFilter securityHeadersFilter,
      InternalApiKeyFilter internalApiKeyFilter,
      RequestAuditContextFilter requestAuditContextFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.securityHeadersFilter = securityHeadersFilter;
    this.internalApiKeyFilter = internalApiKeyFilter;
    this.requestAuditContextFilter = requestAuditContextFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable()) // mesma postura do Quarkus original (sem protecao CSRF explicita)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password",
                "/api/v1/public/**",
                "/api/v1/checkout/**",
                "/api/v1/internal/**",
                "/api/v1/storage/proxy",
                "/webhook/**",
                "/actuator/**",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html")
            .permitAll()
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().permitAll())
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint((request, response, authException) ->
                response.sendError(401, "Nao autenticado")))
        // JwtAuthenticationFilter precisa ser registrado (relativo a um filtro padrao do Spring
        // Security) ANTES de ser usado como ancora abaixo — o builder so conhece a posicao de um
        // filtro customizado depois que ele proprio passa por um addFilter*; inverter esta ordem
        // reproduz "The Filter class ... does not have a registered order" na subida real da app
        // (nao aparece em teste porque SecurityFilterChain nao e exercitado no MockMvc padrao).
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // RequestAuditContextFilter roda antes de tudo (Priority(AUTHENTICATION - 10) no
        // original): preenche requestId/ip/userAgent para o AuditService, mesmo em requisicoes
        // nao autenticadas ou rejeitadas na autenticacao.
        .addFilterBefore(requestAuditContextFilter, JwtAuthenticationFilter.class)
        // /api/v1/internal/* segue em permitAll (nao ha JWT nessas chamadas, igual ao original):
        // quem autentica e o InternalApiKeyFilter, pelo header X-Internal-Api-Key.
        .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  private CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of(
        "accept", "authorization", "content-type", "x-requested-with",
        "x-tenant-id", "x-idempotency-key", "x-request-id"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
