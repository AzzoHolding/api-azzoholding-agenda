package br.com.phdigitalcode.azzo.agenda.pro.service.impl;

import java.math.BigDecimal;
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
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutIntent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.CheckoutOrder;
import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;
import br.com.phdigitalcode.azzo.agenda.pro.entity.PasswordResetToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacUserRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsVersion;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PlanStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import br.com.phdigitalcode.azzo.agenda.pro.entity.id.RbacUserRoleId;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.mapper.UsuarioMapper;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutIntentRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.LicenseEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.PasswordResetTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
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
import br.com.phdigitalcode.azzo.agenda.pro.service.TermsService;
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
 * <p>{@code registrar} (register) agora e o do original, completo. Ate 2026-09-11 era uma versao
 * REDUZIDA que nao gravava o {@code plan_status_id} — coluna NOT NULL, e por isso o cadastro
 * FALHAVA em banco novo —, nao ativava o trial, nao gravava o aceite dos termos e nao barrava o
 * segundo trial do mesmo documento. Os quatro voltaram:
 * <ul>
 *   <li><b>status do plano</b> ACTIVE na criacao; o {@code LicenseStatusService} o recalcula a
 *       partir dos pedidos vigentes, e o pedido de trial abaixo e o que o mantem ACTIVE;
 *   <li><b>trial</b>: um {@code CheckoutIntent} + {@code CheckoutOrder} CONFIRMADOS do produto
 *       marcado {@code is_trial} (semeado na V13, 7 dias), com {@code valid_until} no fim do
 *       periodo — e exatamente o que {@code possuiPlanoVigente} procura — e o evento
 *       {@code TRIAL_ACTIVATED} no historico da licenca;
 *   <li><b>aceite dos termos</b> gravado pelo {@code TermsService} (versao, requestId, IP e hash):
 *       e a PROVA do consentimento que a LGPD exige, e nao so a checagem de que o campo veio;
 *   <li><b>um trial por CPF/CNPJ</b>, comparando o SHA-256 do documento
 *       ({@code Tenant.trialDocumentHash}). O documento nunca vai para o log.
 * </ul>
 */
@Service
public class AuthServiceImpl implements AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);
  private static final String RESET_PASSWORD_MESSAGE =
      "Se o e-mail existir, voce recebera instrucoes para redefinir a senha.";
  /** Quando o produto de trial nao diz a validade, vale a do original. */
  private static final int DEFAULT_TRIAL_DAYS = 7;

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
  private final TermsService termsService;
  private final ProductRepository productRepository;
  private final CheckoutIntentRepository checkoutIntentRepository;
  private final CheckoutOrderRepository checkoutOrderRepository;
  private final LicenseEventRepository licenseEventRepository;

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
      UsuarioMapper usuarioMapper,
      TermsService termsService,
      ProductRepository productRepository,
      CheckoutIntentRepository checkoutIntentRepository,
      CheckoutOrderRepository checkoutOrderRepository,
      LicenseEventRepository licenseEventRepository) {
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
    this.termsService = termsService;
    this.productRepository = productRepository;
    this.checkoutIntentRepository = checkoutIntentRepository;
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.licenseEventRepository = licenseEventRepository;
  }

  @Override
  @Transactional
  public AuthResponse registrar(RegisterRequest request, String requestId, String ipAddress) {
    validarAceiteObrigatorio(request);
    String email = normalizeEmail(request.email);
    String documento = normalizeCpfCnpj(request.cpfCnpj);
    // O trial e UM por documento, e o que se compara e o hash — como no original.
    String hashDoDocumento = sha256Hex(documento);
    // A versao aceita tem que existir e estar ATIVA: e ela que o aceite gravado vai provar.
    TermsVersion termosDeUso = termsService.requireActiveVersion(
        AuditConstants.TermsDocumentType.TERMS_OF_USE, request.termsOfUseVersion);
    TermsVersion politicaDePrivacidade = termsService.requireActiveVersion(
        AuditConstants.TermsDocumentType.PRIVACY_POLICY, request.privacyPolicyVersion);

    if (usuarioRepository.findByEmail(email).isPresent()) {
      LOG.warn(CorrelatedLogging.context("Registro recusado", "email", email, "reason", "duplicate_email"));
      throw new IllegalArgumentException("Ja existe usuario com este email");
    }
    if (tenantRepository.existsByTrialDocumentHash(hashDoDocumento)) {
      // O documento NAO vai para o log (o original o gravava): e dado pessoal, e o motivo basta.
      LOG.warn(CorrelatedLogging.context("Registro recusado", "email", email, "reason", "trial_already_used"));
      throw new IllegalArgumentException("CPF/CNPJ ja utilizou o plano gratuito");
    }

    Tenant tenant = new Tenant();
    tenant.setName(request.salonName != null && !request.salonName.isBlank() ? request.salonName : "Meu Salao");
    tenant.setSlug(SlugUtil.gerarSlug(tenant.getName()) + "-" + UUID.randomUUID().toString().substring(0, 6));
    tenant.setPhone(request.phone);
    tenant.setEmail(email);
    tenant.setDocument(documento);
    tenant.setTrialDocumentHash(hashDoDocumento);
    // plan_status_id e NOT NULL: sem isto o cadastro falhava em banco novo.
    tenant.setPlanStatusId(tenantRepository.buscarPlanStatusIdPorCodigo(PlanStatus.ACTIVE.name())
        .orElseThrow(() -> new IllegalStateException("Status de plano ACTIVE nao encontrado")));
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
    registrarAceitesTermos(usuario, termosDeUso, politicaDePrivacidade, requestId, ipAddress);
    garantirAcessoOwner(usuario.getId());
    ativarTrialTenant(tenant, usuario);

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

  /**
   * Grava a PROVA do aceite dos dois documentos (versao, requestId, IP e hash, pelo
   * {@code TermsService}). O mesmo requestId nos dois registros mostra que foram aceitos no mesmo
   * envio do formulario.
   */
  private void registrarAceitesTermos(
      Usuario usuario,
      TermsVersion termosDeUso,
      TermsVersion politicaDePrivacidade,
      String requestId,
      String ipAddress) {
    String rid = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
    termsService.accept(usuario.getTenantId(), usuario.getId(), termosDeUso.getId(), rid, ipAddress);
    termsService.accept(usuario.getTenantId(), usuario.getId(), politicaDePrivacidade.getId(), rid, ipAddress);
  }

  /**
   * Ativa o trial: um pedido CONFIRMADO, de valor zero, do produto de trial, valido ate o fim do
   * periodo. E o que {@code CheckoutOrderRepository.possuiPlanoVigente} procura — sem ele, o
   * {@code LicenseStatusService} marca o salao recem-criado como EXPIRED e a licenca bloqueia.
   */
  private void ativarTrialTenant(Tenant tenant, Usuario usuario) {
    Product produtoDeTrial = productRepository.findLatestActiveTrial()
        .orElseThrow(() -> new IllegalStateException("Nenhum plano trial ativo configurado"));

    Instant agora = Instant.now();
    Instant fimDoTrial = agora.plusSeconds(resolverDiasDeValidade(produtoDeTrial) * 24L * 60L * 60L);

    CheckoutIntent intencao = new CheckoutIntent();
    intencao.setTenantId(tenant.getId());
    intencao.setUserId(usuario.getId());
    intencao.setProductId(produtoDeTrial.getId());
    intencao.setProductNameSnapshot(produtoDeTrial.getName());
    intencao.setCurrencySnapshot(produtoDeTrial.getCurrency());
    intencao.setCurrency(produtoDeTrial.getCurrency());
    intencao.setUnitPriceSnapshot(BigDecimal.ZERO);
    intencao.setQuantity(1);
    intencao.setTotalPriceSnapshot(BigDecimal.ZERO);
    intencao.setCalculatedTotal(BigDecimal.ZERO);
    intencao.setStatus(StatusCheckout.CONFIRMED);
    intencao.setExpiresAt(fimDoTrial);
    intencao.setPaymentReference("trial-tenant-" + tenant.getId());
    intencao.setConfirmedAt(agora);
    checkoutIntentRepository.save(intencao);

    CheckoutOrder pedido = new CheckoutOrder();
    pedido.setIntentId(intencao.getId());
    pedido.setProductId(produtoDeTrial.getId());
    pedido.setTenantId(tenant.getId());
    pedido.setUserId(usuario.getId());
    pedido.setTotal(0L);
    pedido.setStatus(StatusCheckout.CONFIRMED);
    pedido.setValidUntil(fimDoTrial);
    checkoutOrderRepository.save(pedido);

    licenseEventRepository.save(
        LicenseEvent.of(tenant.getId(), "TRIAL_ACTIVATED", usuario.getId(), produtoDeTrial.getId(), fimDoTrial));
  }

  private int resolverDiasDeValidade(Product produto) {
    if (produto.getValidityDays() != null && produto.getValidityDays() > 0) {
      return produto.getValidityDays();
    }
    if (produto.getValidityMonths() > 0) {
      return produto.getValidityMonths() * 30;
    }
    return DEFAULT_TRIAL_DAYS;
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
