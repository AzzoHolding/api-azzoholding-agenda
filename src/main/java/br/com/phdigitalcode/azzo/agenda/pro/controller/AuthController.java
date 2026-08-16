package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ForgotPasswordRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RefreshTokenRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RegisterRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ResetPasswordRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AuthResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.GenericMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.UsuarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthCookieService;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtPrincipal;
import br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService;
import br.com.phdigitalcode.azzo.agenda.pro.mapper.UsuarioMapper;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuthService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Equivalente Spring de {@code modules/auth/api/AuthResource.java} (JAX-RS -> Spring MVC).
 * Preserva paths ({@code /api/v1/auth/*}), metodos HTTP, codigos de resposta e o contrato de
 * cookies httpOnly (ver {@code AuthCookieService}).
 *
 * <p>NOTA (RESOLVIDA): {@code MenuConfigResource} do original expunha
 * {@code /api/v1/config/menus/*} (nao {@code /api/v1/auth/menu} — o path real e outro recurso);
 * ficou pendente nas etapas iniciais deste modulo, com apenas {@code /me} (via {@link #me()})
 * portado. O gap foi fechado em {@code MenuConfigController}
 * ({@code br.com.phdigitalcode.azzo.agenda.pro.controller.MenuConfigController}), que porta os 8
 * endpoints originais (menu dinamico, overrides de RBAC fino por rota, catalogo de menu) apoiado
 * em {@code MenuService}/{@code MenuOverrideService}/{@code MenuCatalogService} e no cache
 * {@code menu-routes-by-tenant-role} (ja declarado em {@code CacheConfig}). Ver
 * {@code MIGRACAO-QUARKUS-SPRING.md}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private static final Logger LOG = LoggerFactory.getLogger(AuthController.class);

  private final AuthService authService;
  private final AuthCookieService authCookieService;
  private final RefreshTokenService refreshTokenService;
  private final UsuarioRepository usuarioRepository;
  private final UsuarioMapper usuarioMapper;

  public AuthController(
      AuthService authService,
      AuthCookieService authCookieService,
      RefreshTokenService refreshTokenService,
      UsuarioRepository usuarioRepository,
      UsuarioMapper usuarioMapper) {
    this.authService = authService;
    this.authCookieService = authCookieService;
    this.refreshTokenService = refreshTokenService;
    this.usuarioRepository = usuarioRepository;
    this.usuarioMapper = usuarioMapper;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse authResponse = authService.login(request);
    return buildCookieAuthResponse(authResponse);
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    String requestId = firstNonBlank(
        servletRequest.getHeader("X-Request-Id"), servletRequest.getHeader("x-request-id"));
    String forwardedFor = firstNonBlank(
        servletRequest.getHeader("X-Forwarded-For"), servletRequest.getHeader("x-forwarded-for"));
    String ipAddress = firstIp(forwardedFor);
    if (ipAddress == null) {
      ipAddress = firstNonBlank(servletRequest.getHeader("X-Real-IP"), servletRequest.getHeader("x-real-ip"));
    }

    AuthResponse authResponse = authService.registrar(request, requestId, ipAddress);
    return buildCookieAuthResponse(authResponse);
  }

  @PostMapping("/forgot-password")
  public GenericMessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    return authService.requestPasswordReset(request.email);
  }

  @PostMapping("/reset-password")
  public GenericMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    return authService.resetPassword(request);
  }

  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
      @RequestBody(required = false) RefreshTokenRequest request) {
    try {
      String refreshToken = resolveRefreshToken(refreshTokenCookie, request);
      AuthResponse authResponse = authService.refresh(refreshToken);
      return buildCookieAuthResponse(authResponse);
    } catch (ApiClientErrorException ex) {
      LOG.warn(CorrelatedLogging.context(
          "Refresh recusado",
          "reason", "session_expired",
          "status", ex.getStatus(),
          "refreshTokenPresent", (refreshTokenCookie != null && !refreshTokenCookie.isBlank())
              || (request != null && request.refresh_token != null && !request.refresh_token.isBlank())));
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .header(HttpHeaders.SET_COOKIE, authCookieService.clearAccessTokenCookie().toString())
          .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshTokenCookie().toString())
          .body(Map.of("message", "Sessao expirada. Faca login novamente."));
    }
  }

  @DeleteMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie) {
    authService.logout(refreshTokenCookie);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, authCookieService.clearAccessTokenCookie().toString())
        .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshTokenCookie().toString())
        .build();
  }

  @GetMapping("/me")
  public UsuarioResponse me() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication != null && authentication.getPrincipal() instanceof JwtPrincipal jwtPrincipal)) {
      throw new ApiClientErrorException("Nao autenticado", 401);
    }
    return usuarioRepository.findById(jwtPrincipal.userId())
        .map(usuarioMapper::toResponse)
        .orElseThrow(() -> new ApiClientErrorException("Usuario nao encontrado", 401));
  }

  private String firstIp(String forwardedFor) {
    if (forwardedFor == null || forwardedFor.isBlank()) return null;
    String[] values = forwardedFor.split(",");
    if (values.length == 0) return null;
    String first = values[0] != null ? values[0].trim() : null;
    return (first == null || first.isBlank()) ? null : first;
  }

  private String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b;
  }

  private String resolveRefreshToken(String refreshTokenCookie, RefreshTokenRequest request) {
    if (refreshTokenCookie != null && !refreshTokenCookie.isBlank()) return refreshTokenCookie;
    if (request != null && request.refresh_token != null && !request.refresh_token.isBlank()) {
      return request.refresh_token;
    }
    return null;
  }

  private ResponseEntity<AuthResponse> buildCookieAuthResponse(AuthResponse authResponse) {
    String accessToken = authResponse.access_token;
    String refreshToken = authResponse.refresh_token;

    AuthResponse payload = new AuthResponse();
    payload.user = authResponse.user;
    payload.expires_in = authResponse.expires_in;
    payload.token_type = authResponse.token_type;

    ResponseCookie accessCookie = authCookieService.buildAccessTokenCookie(accessToken, authResponse.expires_in);
    ResponseCookie refreshCookie = authCookieService.buildRefreshTokenCookie(
        refreshToken, refreshTokenService.refreshTokenExpiresInSeconds());

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(payload);
  }
}
