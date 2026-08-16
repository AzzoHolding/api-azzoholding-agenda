package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import at.favre.lib.crypto.bcrypt.BCrypt;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.LoginRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RegisterRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.ResetPasswordRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.AuthResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.GenericMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.PasswordResetToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacUserRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.id.RbacUserRoleId;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.mapper.UsuarioMapper;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PasswordResetTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacAuthorizationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RbacUserRoleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.JwtService;
import br.com.phdigitalcode.azzo.agenda.pro.security.PasswordPolicyValidator;
import br.com.phdigitalcode.azzo.agenda.pro.security.RefreshTokenService;
import br.com.phdigitalcode.azzo.agenda.pro.security.TotpService;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuthService;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;
import br.com.phdigitalcode.azzo.agenda.pro.util.SlugUtil;

/**
 * Equivalente Spring de {@code modules/auth/application/ServicoAuth.java}.
 *
 * <p>NOTA DE ESCOPO (ver {@code MIGRACAO-QUARKUS-SPRING.md}): {@code login}, {@code refresh},
 * {@code logout}, {@code requestPasswordReset} e {@code resetPassword} sao portados com
 * fidelidade total ao original — dependem apenas de entidades fundacionais ja migradas
 * (Usuario, RefreshToken, PasswordResetToken, RBAC).
 *
 * <p>{@code registrar} (register) e uma versao SIMPLIFICADA e DELIBERADAMENTE REDUZIDA do
 * original: o Quarkus original tambem (a) valida e persiste aceite de Termos de Uso/Privacidade
 * via {@code TermsService} (modulo {@code audit}), (b) ativa um plano trial completo via
 * {@code CheckoutIntent}/{@code CheckoutOrder}/{@code Product} (modulo {@code billing}), e
 * (c) verifica duplicidade de CPF/CNPJ de trial via {@code Tenant.trialDocumentHash}. Nenhum
 * desses modulos foi migrado ainda nesta etapa (auth e security/common sao fundacionais; billing,
 * audit/terms e professionals sao dominios de negocio que dependem de auth, nao o contrario).
 * Portanto esta implementacao: cria o {@code Tenant} (projecao minima, ver entidade
 * {@link Tenant}) e o {@code Usuario} OWNER, concede a role/permissoes de owner (RBAC), mas NAO
 * persiste aceite de termos, NAO ativa trial e NAO verifica duplicidade de documento. Isso deve
 * ser resolvido quando os modulos {@code billing}/{@code audit}/{@code tenant} forem portados —
 * ver pendencias registradas no documento de migracao.
 */
@Service
public class AuthServiceImpl implements AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);
  private static final String RESET_PASSWORD_MESSAGE =
      "Se o e-mail existir, voce recebera instrucoes para redefinir a senha.";

  private static final int EMAIL_LOCKOUT_MAX_ATTEMPTS = 5;
  private static final Duration EMAIL_LOCKOUT_WINDOW = Duration.ofMinutes(15);

  private record LoginAttemptEntry(AtomicInteger count, Instant windowStart) {}

  private final ConcurrentHashMap<String, LoginAttemptEntry> emailLoginAttempts = new ConcurrentHashMap<>();

  private final TenantRepository tenantRepository;
  private final UsuarioRepository usuarioRepository;
  private final RbacRoleRepository rbacRoleRepository;
  private final RbacUserRoleRepository rbacUserRoleRepository;
  private final RbacAuthorizationRepository rbacAuthorizationRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final EncryptionService encryptionService;
  private final TotpService totpService;
  private final AuditService auditService;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final EmailJobService emailJobService;
  private final UsuarioMapper usuarioMapper;

  @Value("${app.public.booking.base-url:http://localhost:5173}")
  private String publicFrontendBaseUrl;

  public AuthServiceImpl(
      TenantRepository tenantRepository,
      UsuarioRepository usuarioRepository,
      RbacRoleRepository rbacRoleRepository,
      RbacUserRoleRepository rbacUserRoleRepository,
      RbacAuthorizationRepository rbacAuthorizationRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      EncryptionService encryptionService,
      TotpService totpService,
      AuditService auditService,
      PasswordPolicyValidator passwordPolicyValidator,
      EmailJobService emailJobService,
      UsuarioMapper usuarioMapper) {
    this.tenantRepository = tenantRepository;
    this.usuarioRepository = usuarioRepository;
    this.rbacRoleRepository = rbacRoleRepository;
    this.rbacUserRoleRepository = rbacUserRoleRepository;
    this.rbacAuthorizationRepository = rbacAuthorizationRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.encryptionService = encryptionService;
    this.totpService = totpService;
    this.auditService = auditService;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.emailJobService = emailJobService;
    this.usuarioMapper = usuarioMapper;
  }

  @Override
  @Transactional
  public AuthResponse registrar(RegisterRequest request, String requestId, String ipAddress) {
    validarAceiteObrigatorio(request);
    String email = normalizeEmail(request.email);
    normalizeCpfCnpj(request.cpfCnpj); // valida formato; dedup de trial fica pendente (ver javadoc da classe)

    if (usuarioRepository.findByEmail(email).isPresent()) {
      LOG.warn(CorrelatedLogging.context("Registro recusado", "email", email, "reason", "duplicate_email"));
      throw new IllegalArgumentException("Ja existe usuario com este email");
    }

    Tenant tenant = new Tenant();
    tenant.setName(request.salonName != null && !request.salonName.isBlank() ? request.salonName : "Meu Salao");
    tenant.setSlug(SlugUtil.gerarSlug(tenant.getName()) + "-" + UUID.randomUUID().toString().substring(0, 6));
    tenant.setPhone(request.phone);
    tenant.setEmail(email);
    tenantRepository.save(tenant);

    Usuario usuario = new Usuario();
    usuario.setTenantId(tenant.getId());
    usuario.setName(request.name);
    usuario.setEmail(email);
    usuario.setPhone(request.phone);
    usuario.setRole(PapelUsuario.OWNER);
    validarCriacaoUsuarioPorRole(usuario.getRole());
    passwordPolicyValidator.validateOrThrow(request.password);
    usuario.setPasswordHash(BCrypt.withDefaults().hashToString(12, request.password.toCharArray()));

    usuarioRepository.save(usuario);
    garantirAcessoOwner(usuario.getId());

    AuthResponse response = montarResposta(usuario);
    registrarAuditoriaAuth(
        usuario.getTenantId(),
        usuario.getId(),
        AuditConstants.Status.SUCCESS,
        "AUTH_REGISTER",
        null,
        Map.of("email", usuario.getEmail(), "role", usuario.getRole().name()));
    LOG.info(CorrelatedLogging.context(
        "Registro concluido",
        "tenantId", usuario.getTenantId(),
        "userId", usuario.getId(),
        "email", usuario.getEmail(),
        "role", usuario.getRole().name()));
    return response;
  }

  @Override
  @Transactional
  public AuthResponse login(LoginRequest request) {
    String normalizedEmail = normalizeEmail(request.email);
    checkEmailLoginLockout(normalizedEmail);

    Usuario usuario = usuarioRepository.findByEmail(normalizedEmail).orElse(null);
    if (usuario == null) {
      LOG.warn(CorrelatedLogging.context("Login recusado", "email", normalizedEmail, "reason", "user_not_found"));
      throw new IllegalArgumentException("Credenciais invalidas");
    }

    boolean ok = BCrypt.verifyer().verify(request.password.toCharArray(), usuario.getPasswordHash()).verified;
    if (!ok) {
      recordFailedEmailLogin(normalizedEmail);
      LOG.warn(CorrelatedLogging.context(
          "Login recusado", "tenantId", usuario.getTenantId(), "userId", usuario.getId(),
          "email", usuario.getEmail(), "reason", "invalid_password"));
      registrarAuditoriaAuth(
          usuario.getTenantId(), usuario.getId(), AuditConstants.Status.DENIED, "AUTH_LOGIN",
          Map.of("email", usuario.getEmail()), null);
      throw new IllegalArgumentException("Credenciais invalidas");
    }
    emailLoginAttempts.remove(normalizedEmail);

    // NOTA: verificacao de profissional inativo (modulo professionals) fica pendente ate esse
    // modulo ser migrado — ver JavaDoc da classe.

    if (requiresMfaForLogin(usuario) && !isMfaCodePresent(request)) {
      LOG.warn(CorrelatedLogging.context(
          "Login recusado", "tenantId", usuario.getTenantId(), "userId", usuario.getId(),
          "email", usuario.getEmail(), "reason", "mfa_required"));
      registrarAuditoriaAuth(
          usuario.getTenantId(), usuario.getId(), AuditConstants.Status.DENIED, "AUTH_LOGIN_MFA_REQUIRED",
          Map.of("email", usuario.getEmail()), null);
      throw new ApiClientErrorException("Codigo MFA obrigatorio", 428);
    }

    if (requiresMfaForLogin(usuario)) {
      String secret = decryptMfaSecretOrThrow(usuario);
      boolean mfaOk = totpService.verifyCode(secret, request.mfaCode);
      if (!mfaOk) {
        LOG.warn(CorrelatedLogging.context(
            "Login recusado", "tenantId", usuario.getTenantId(), "userId", usuario.getId(),
            "email", usuario.getEmail(), "reason", "invalid_mfa_code"));
        registrarAuditoriaAuth(
            usuario.getTenantId(), usuario.getId(), AuditConstants.Status.DENIED, "AUTH_LOGIN_MFA_DENIED",
            Map.of("email", usuario.getEmail()), null);
        throw new IllegalArgumentException("Codigo MFA invalido");
      }
    }

    AuthResponse response = montarResposta(usuario);
    registrarAuditoriaAuth(
        usuario.getTenantId(), usuario.getId(), AuditConstants.Status.SUCCESS, "AUTH_LOGIN",
        null, Map.of("email", usuario.getEmail()));
    LOG.info(CorrelatedLogging.context(
        "Login concluido", "tenantId", usuario.getTenantId(), "userId", usuario.getId(), "email", usuario.getEmail()));
    return response;
  }

  @Override
  @Transactional
  public AuthResponse refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      LOG.warn(CorrelatedLogging.context("Refresh recusado", "reason", "refresh_token_missing"));
      throw new ApiClientErrorException("refresh_token obrigatorio", 400);
    }
    RefreshTokenService.RefreshSession session = refreshTokenService.rotateAndGetSession(refreshToken);
    LOG.info(CorrelatedLogging.context(
        "Refresh concluido", "tenantId", session.user().getTenantId(), "userId", session.user().getId()));
    return montarResposta(session.user(), session.refreshTokenRaw());
  }

  @Override
  @Transactional
  public void logout(String refreshToken) {
    refreshTokenService.revokeByRawToken(refreshToken);
    LOG.info(CorrelatedLogging.context(
        "Logout concluido", "refreshTokenPresent", refreshToken != null && !refreshToken.isBlank()));
  }

  @Override
  @Transactional
  public GenericMessageResponse requestPasswordReset(String email) {
    String normalizedEmail = normalizeEmail(email);
    Usuario usuario = usuarioRepository.findByEmail(normalizedEmail).orElse(null);
    if (usuario == null) {
      LOG.info(CorrelatedLogging.context("Solicitacao de reset ignorada", "email", normalizedEmail, "reason", "user_not_found"));
      return new GenericMessageResponse(RESET_PASSWORD_MESSAGE);
    }

    Instant now = Instant.now();
    passwordResetTokenRepository.markAllActiveAsUsedByUser(usuario.getId(), now);

    String rawToken = generateResetToken();
    PasswordResetToken token = new PasswordResetToken();
    token.setTenantId(usuario.getTenantId());
    token.setUserId(usuario.getId());
    token.setTokenHash(sha256Hex(rawToken));
    token.setExpiresAt(now.plus(PASSWORD_RESET_TOKEN_TTL));
    passwordResetTokenRepository.save(token);

    String resetUrl = buildResetUrl(rawToken);

    registrarAuditoriaAuth(
        usuario.getTenantId(), usuario.getId(), AuditConstants.Status.SUCCESS, "AUTH_FORGOT_PASSWORD_REQUEST",
        null, Map.of("email", usuario.getEmail()));

    emailJobService.enqueuePasswordReset(usuario, token, resetUrl);
    LOG.info(CorrelatedLogging.context(
        "Solicitacao de reset criada", "tenantId", usuario.getTenantId(), "userId", usuario.getId(), "email", usuario.getEmail()));

    return new GenericMessageResponse(RESET_PASSWORD_MESSAGE);
  }

  @Override
  @Transactional
  public GenericMessageResponse resetPassword(ResetPasswordRequest request) {
    if (request == null || request.token == null || request.token.isBlank()) {
      LOG.warn(CorrelatedLogging.context("Reset de senha recusado", "reason", "token_missing"));
      throw new ApiClientErrorException("Token de redefinicao obrigatorio", 400);
    }
    passwordPolicyValidator.validateOrThrow(request.password);

    Instant now = Instant.now();
    PasswordResetToken token = passwordResetTokenRepository
        .findActiveByHash(sha256Hex(request.token.trim()), now)
        .orElseThrow(() -> new ApiClientErrorException("Token de redefinicao invalido ou expirado", 400));

    Usuario usuario = usuarioRepository.findById(token.getUserId())
        .orElseThrow(() -> {
          LOG.warn(CorrelatedLogging.context("Reset de senha recusado", "userId", token.getUserId(), "reason", "token_user_not_found"));
          return new ApiClientErrorException("Usuario do token nao encontrado", 400);
        });

    usuario.setPasswordHash(BCrypt.withDefaults().hashToString(12, request.password.toCharArray()));
    usuarioRepository.save(usuario);
    token.setUsedAt(now);
    passwordResetTokenRepository.save(token);
    passwordResetTokenRepository.markAllActiveAsUsedByUser(usuario.getId(), now);
    refreshTokenService.revokeAllForUser(usuario.getId());

    registrarAuditoriaAuth(
        usuario.getTenantId(), usuario.getId(), AuditConstants.Status.SUCCESS, "AUTH_PASSWORD_RESET",
        Map.of("email", usuario.getEmail()), Map.of("passwordReset", true));
    LOG.info(CorrelatedLogging.context(
        "Reset de senha concluido", "tenantId", usuario.getTenantId(), "userId", usuario.getId(), "email", usuario.getEmail()));

    return new GenericMessageResponse("Senha redefinida com sucesso.");
  }

  private void registrarAuditoriaAuth(UUID tenantId, UUID actorUserId, String status, String action, Object before, Object after) {
    if (tenantId == null) return;
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = actorUserId;
      command.module = AuditConstants.Module.AUTH;
      command.action = action;
      command.entityType = "USER_AUTH";
      command.entityId = actorUserId != null ? actorUserId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      if (AuditConstants.Status.SUCCESS.equals(status)) {
        auditService.recordSuccess(command);
      } else if (AuditConstants.Status.DENIED.equals(status)) {
        auditService.recordDenied(command);
      } else {
        auditService.recordError(command);
      }
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar autenticacao.
    }
  }

  private void validarAceiteObrigatorio(RegisterRequest request) {
    if (request == null) throw new IllegalArgumentException("Request obrigatorio");
    if (!Boolean.TRUE.equals(request.acceptedTermsOfUse)) {
      throw new IllegalArgumentException("Aceite dos Termos de Uso e obrigatorio");
    }
    if (!Boolean.TRUE.equals(request.acceptedPrivacyPolicy)) {
      throw new IllegalArgumentException("Aceite da Politica de Privacidade e obrigatorio");
    }
    if (request.termsOfUseVersion == null || request.termsOfUseVersion.isBlank()) {
      throw new IllegalArgumentException("Versao dos Termos de Uso obrigatoria");
    }
    if (request.privacyPolicyVersion == null || request.privacyPolicyVersion.isBlank()) {
      throw new IllegalArgumentException("Versao da Politica de Privacidade obrigatoria");
    }
  }

  private AuthResponse montarResposta(Usuario usuario) {
    String refreshToken = refreshTokenService.issueForUser(usuario);
    return montarResposta(usuario, refreshToken);
  }

  private AuthResponse montarResposta(Usuario usuario, String refreshToken) {
    String token = jwtService.gerarToken(usuario);

    AuthResponse resp = new AuthResponse();
    resp.access_token = token;
    resp.refresh_token = refreshToken;
    resp.expires_in = jwtService.accessTokenExpiresInSeconds();
    resp.user = usuarioMapper.toResponse(usuario);
    return resp;
  }

  private void checkEmailLoginLockout(String email) {
    LoginAttemptEntry entry = emailLoginAttempts.get(email);
    if (entry == null) return;
    if (entry.windowStart().isBefore(Instant.now().minus(EMAIL_LOCKOUT_WINDOW))) {
      emailLoginAttempts.remove(email);
      return;
    }
    if (entry.count().get() >= EMAIL_LOCKOUT_MAX_ATTEMPTS) {
      throw new ApiClientErrorException("Muitas tentativas de login. Aguarde 15 minutos e tente novamente.", 429);
    }
  }

  private void recordFailedEmailLogin(String email) {
    emailLoginAttempts.compute(email, (k, existing) -> {
      if (existing == null || existing.windowStart().isBefore(Instant.now().minus(EMAIL_LOCKOUT_WINDOW))) {
        return new LoginAttemptEntry(new AtomicInteger(1), Instant.now());
      }
      existing.count().incrementAndGet();
      return existing;
    });
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) throw new IllegalArgumentException("Email obrigatorio");
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String generateResetToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String buildResetUrl(String rawToken) {
    String baseUrl = publicFrontendBaseUrl == null ? "" : publicFrontendBaseUrl.trim();
    if (baseUrl.isBlank() || "__unset__".equalsIgnoreCase(baseUrl)) {
      throw new IllegalStateException("PUBLIC_BOOKING_BASE_URL nao configurado para redefinicao de senha");
    }
    if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    return baseUrl + "/redefinir-senha?token=" + rawToken;
  }

  private boolean requiresMfaForLogin(Usuario usuario) {
    return usuario != null && usuario.isMfaEnabled();
  }

  private void validarCriacaoUsuarioPorRole(PapelUsuario targetRole) {
    if (PapelUsuario.ADMIN.equals(targetRole)) {
      throw new ApiClientErrorException("Nao e permitido criar usuario com role ADMIN via registro publico", 403);
    }
  }

  private boolean isMfaCodePresent(LoginRequest request) {
    return request != null && request.mfaCode != null && !request.mfaCode.isBlank();
  }

  private String decryptMfaSecretOrThrow(Usuario usuario) {
    String secret = encryptionService.decrypt(usuario.getMfaSecretEnc());
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("MFA ativo sem secret configurado para o usuario");
    }
    return secret;
  }

  private String normalizeCpfCnpj(String cpfCnpj) {
    if (cpfCnpj == null || cpfCnpj.isBlank()) {
      throw new IllegalArgumentException("CPF/CNPJ obrigatorio para ativar plano gratuito");
    }
    String digits = cpfCnpj.replaceAll("\\D", "");
    if (digits.length() != 11 && digits.length() != 14) {
      throw new IllegalArgumentException("CPF/CNPJ invalido");
    }
    return digits;
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 nao disponivel", e);
    }
  }

  private void garantirAcessoOwner(UUID userId) {
    RbacRole ownerRole = rbacRoleRepository.findByNameIgnoreCase(PapelUsuario.OWNER.name())
        .orElseGet(() -> {
          RbacRole role = new RbacRole();
          role.setName(PapelUsuario.OWNER.name());
          return rbacRoleRepository.save(role);
        });

    RbacUserRoleId userRoleId = new RbacUserRoleId();
    userRoleId.setUserId(userId);
    userRoleId.setRoleId(ownerRole.getId());
    if (rbacUserRoleRepository.findById(userRoleId).isEmpty()) {
      RbacUserRole userRole = new RbacUserRole();
      userRole.setId(userRoleId);
      rbacUserRoleRepository.save(userRole);
    }

    rbacAuthorizationRepository.grantAllPermissionsToRole(ownerRole.getId());
  }
}
