package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RefreshToken;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RefreshTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/** Equivalente Spring de {@code modules/auth/infrastructure/security/RefreshTokenService.java}. */
@Service
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final UsuarioRepository usuarioRepository;
  private final TokenRevocationService tokenRevocationService;

  @Value("${app.security.refresh-token.ttl-days:30}")
  private int refreshTokenTtlDays;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      UsuarioRepository usuarioRepository,
      TokenRevocationService tokenRevocationService) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.usuarioRepository = usuarioRepository;
    this.tokenRevocationService = tokenRevocationService;
  }

  @Transactional
  public String issueForUser(Usuario user) {
    String rawToken = generateTokenValue();
    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUserId(user.getId());
    refreshToken.setTenantId(user.getTenantId());
    refreshToken.setTokenHash(hashToken(rawToken));
    refreshToken.setExpiresAt(Instant.now().plus(Duration.ofDays(Math.max(1, refreshTokenTtlDays))));
    refreshTokenRepository.save(refreshToken);
    return rawToken;
  }

  @Transactional
  public RefreshSession rotateAndGetSession(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new ApiClientErrorException("refresh_token invalido", 401);
    }
    Instant now = Instant.now();
    String currentHash = hashToken(rawRefreshToken.trim());
    RefreshToken current = refreshTokenRepository.findActiveByHash(currentHash, now)
        .orElseThrow(() -> new ApiClientErrorException("refresh_token invalido ou expirado", 401));

    Usuario user = usuarioRepository.findById(current.getUserId())
        .orElseThrow(() -> new ApiClientErrorException("Usuario do refresh_token nao encontrado", 401));
    if (current.getTenantId() != null && user.getTenantId() != null
        && !current.getTenantId().equals(user.getTenantId())) {
      throw new ApiClientErrorException("refresh_token invalido para o tenant atual", 401);
    }

    String newRawToken = generateTokenValue();
    String newHash = hashToken(newRawToken);
    RefreshToken replacement = new RefreshToken();
    replacement.setUserId(user.getId());
    replacement.setTenantId(user.getTenantId());
    replacement.setTokenHash(newHash);
    replacement.setExpiresAt(now.plus(Duration.ofDays(Math.max(1, refreshTokenTtlDays))));
    refreshTokenRepository.save(replacement);

    current.setRevokedAt(now);
    current.setReplacedByTokenHash(newHash);
    refreshTokenRepository.save(current);

    return new RefreshSession(user, newRawToken);
  }

  @Transactional
  public void revokeAllForUser(UUID userId) {
    Instant now = Instant.now();
    refreshTokenRepository.revokeAllByUser(userId, now);
    usuarioRepository.findById(userId).ifPresent(user -> {
      user.setTokensRevokedBefore(now);
      usuarioRepository.save(user);
    });
    tokenRevocationService.invalidateCache(userId);
  }

  @Transactional
  public void revokeByRawToken(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
    Instant now = Instant.now();
    String hash = hashToken(rawRefreshToken.trim());
    refreshTokenRepository.findActiveByHash(hash, now).ifPresent(token -> {
      token.setRevokedAt(now);
      refreshTokenRepository.save(token);
    });
  }

  public long refreshTokenExpiresInSeconds() {
    return Duration.ofDays(Math.max(1, refreshTokenTtlDays)).toSeconds();
  }

  private String generateTokenValue() {
    byte[] bytes = new byte[64];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hashToken(String rawToken) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar hash do refresh_token", e);
    }
  }

  public record RefreshSession(Usuario user, String refreshTokenRaw) {}
}
