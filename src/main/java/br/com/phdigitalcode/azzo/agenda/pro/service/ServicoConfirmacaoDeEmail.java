package br.com.phdigitalcode.azzo.agenda.pro.service;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.GenericMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailVerificationToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailVerificationTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/**
 * Confirmacao de e-mail do cadastro. Prova que quem se cadastrou controla a caixa de entrada
 * informada — o cadastro so confere o formato do e-mail, e o periodo gratuito e um por documento.
 *
 * <p>So o cadastro liga {@code email_confirmation_pending}; ate abrir o link a conta nao entra
 * (o login recusa com 403, DEPOIS de conferir a senha, para a resposta nao revelar que o e-mail
 * existe). Redefinir a senha pelo link do e-mail tambem confirma: prova a mesma posse.
 */
@Service
public class ServicoConfirmacaoDeEmail {

  private static final Logger LOG = LoggerFactory.getLogger(ServicoConfirmacaoDeEmail.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  public static final Duration VALIDADE_DO_LINK = Duration.ofHours(24);
  public static final String MENSAGEM_DO_LOGIN =
      "Confirme seu e-mail para entrar. Enviamos um link para o endereco cadastrado.";
  public static final String MENSAGEM_DO_CADASTRO =
      "Enviamos um link de confirmacao para o seu e-mail. Abra-o para ativar o acesso.";
  static final String MENSAGEM_DO_REENVIO =
      "Se houver uma conta aguardando confirmacao neste e-mail, enviaremos um novo link.";

  private final EmailVerificationTokenRepository tokenRepository;
  private final UsuarioRepository usuarioRepository;
  private final TenantRepository tenantRepository;
  private final EmailJobService emailJobService;
  private final AuditService auditService;
  private final String publicFrontendBaseUrl;

  public ServicoConfirmacaoDeEmail(
      EmailVerificationTokenRepository tokenRepository,
      UsuarioRepository usuarioRepository,
      TenantRepository tenantRepository,
      EmailJobService emailJobService,
      AuditService auditService,
      @Value("${app.public.booking.base-url:http://localhost:5173}") String publicFrontendBaseUrl) {
    this.tokenRepository = tokenRepository;
    this.usuarioRepository = usuarioRepository;
    this.tenantRepository = tenantRepository;
    this.emailJobService = emailJobService;
    this.auditService = auditService;
    this.publicFrontendBaseUrl = publicFrontendBaseUrl;
  }

  /**
   * Marca o usuario como pendente e enfileira o e-mail. Roda na transacao do CADASTRO: se o envio
   * nao puder ser enfileirado, o cadastro inteiro desfaz, em vez de criar uma conta que ninguem
   * consegue ativar. Quem chama salva o usuario.
   */
  public void iniciar(Usuario usuario) {
    usuario.setEmailConfirmationPending(true);
    enfileirarLink(usuario);
  }

  @Transactional
  public GenericMessageResponse confirmar(String tokenPuro) {
    if (tokenPuro == null || tokenPuro.isBlank()) {
      throw new ApiClientErrorException("Link de confirmacao invalido ou expirado", 400);
    }
    Instant agora = Instant.now();
    EmailVerificationToken token =
        tokenRepository
            .findActiveByHash(sha256Hex(tokenPuro.trim()), agora)
            .orElseThrow(() -> new ApiClientErrorException("Link de confirmacao invalido ou expirado", 400));
    Usuario usuario =
        usuarioRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new ApiClientErrorException("Link de confirmacao invalido ou expirado", 400));

    usuario.setEmailConfirmationPending(false);
    usuarioRepository.save(usuario);
    token.setUsedAt(agora);
    tokenRepository.save(token);
    tokenRepository.markAllActiveAsUsedByUser(usuario.getId(), agora);

    reivindicarDocumentoDeTrial(usuario.getTenantId());
    auditar(usuario, "AUTH_EMAIL_CONFIRMED");
    LOG.info("E-mail confirmado tenantId={} userId={}", usuario.getTenantId(), usuario.getId());
    return new GenericMessageResponse("E-mail confirmado. Voce ja pode entrar.");
  }

  /**
   * Recoloca o documento do periodo gratuito em quem acabou de provar a posse do e-mail (achado
   * SEG-009). Se o cadastro ficou tempo demais sem confirmar, o documento foi liberado
   * ({@link ServicoLiberacaoDeDocumentoDeTrial}); confirmar depois o reivindica de novo, SE ninguem o
   * tiver pego nesse meio tempo. Sem isto, bastava esperar o prazo, confirmar e se cadastrar outra vez
   * com o mesmo documento para ter um segundo periodo gratuito. Nunca falha a confirmacao.
   */
  @Transactional
  public void reivindicarDocumentoDeTrial(UUID tenantId) {
    if (tenantId == null) return;
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              if (tenant.getTrialDocumentHash() != null) return;
              String documento = tenant.getDocument();
              if (documento == null || documento.isBlank()) return;
              String hash = sha256Hex(documento.replaceAll("\\D", ""));
              if (tenantRepository.existsByTrialDocumentHash(hash)) return; // outro salao ja pegou
              tenant.setTrialDocumentHash(hash);
              tenantRepository.save(tenant);
            });
  }

  /** Resposta IGUAL exista ou nao a conta (nao vira verificador de e-mails cadastrados). */
  @Transactional
  public GenericMessageResponse reenviar(String email) {
    if (email != null && !email.isBlank()) {
      Usuario usuario =
          usuarioRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
      if (usuario != null && usuario.isEmailConfirmationPending()) {
        tokenRepository.markAllActiveAsUsedByUser(usuario.getId(), Instant.now());
        enfileirarLink(usuario);
        auditar(usuario, "AUTH_EMAIL_CONFIRMATION_RESENT");
      }
    }
    return new GenericMessageResponse(MENSAGEM_DO_REENVIO);
  }

  private void enfileirarLink(Usuario usuario) {
    String puro = novoTokenPuro();
    EmailVerificationToken token = new EmailVerificationToken();
    token.setTenantId(usuario.getTenantId());
    token.setUserId(usuario.getId());
    token.setTokenHash(sha256Hex(puro));
    token.setExpiresAt(Instant.now().plus(VALIDADE_DO_LINK));
    tokenRepository.save(token);
    emailJobService.enqueueEmailVerification(usuario, token, montarLink(puro));
  }

  private String montarLink(String tokenPuro) {
    String base = publicFrontendBaseUrl == null ? "" : publicFrontendBaseUrl.trim();
    if (base.isBlank() || "__unset__".equalsIgnoreCase(base)) {
      throw new IllegalStateException("PUBLIC_BOOKING_BASE_URL nao configurado para confirmacao de e-mail");
    }
    if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + "/confirmar-email?token=" + tokenPuro;
  }

  private void auditar(Usuario usuario, String acao) {
    if (usuario.getTenantId() == null) return;
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = usuario.getTenantId();
      command.actorUserId = usuario.getId();
      command.module = AuditConstants.Module.AUTH;
      command.action = acao;
      command.entityType = "USER_AUTH";
      command.entityId = usuario.getId() != null ? usuario.getId().toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.metadata = Map.of("email", usuario.getEmail());
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria nao deve quebrar a confirmacao.
    }
  }

  private static String novoTokenPuro() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String sha256Hex(String entrada) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(entrada.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 nao disponivel", e);
    }
  }
}
