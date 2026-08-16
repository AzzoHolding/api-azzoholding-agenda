package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;

/**
 * Equivalente Spring de {@code modules/auth/infrastructure/security/TokenRevocationAugmentor.java}
 * (l{@code SecurityIdentityAugmentor} do Quarkus). Rejeita JWTs emitidos antes de
 * {@code tokensRevokedBefore} do usuario, garantindo revogacao imediata de sessoes apos troca de
 * senha / logout-everywhere. Cache TTL de 60s (mesmo valor do original) para evitar leitura de
 * banco a cada request. Usado por {@link JwtAuthenticationFilter}.
 */
@Service
public class TokenRevocationService {

  private static final Duration CACHE_TTL = Duration.ofSeconds(60);

  private final UsuarioRepository usuarioRepository;

  private record CacheEntry(Instant revokedBefore, Instant cachedAt) {}

  private final ConcurrentHashMap<UUID, CacheEntry> cache = new ConcurrentHashMap<>();

  public TokenRevocationService(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  /** @return true se o token (identificado pelo iat) foi revogado. */
  public boolean isRevoked(UUID userId, long issuedAtEpochSecond) {
    Instant revokedBefore = resolveRevokedBefore(userId);
    return revokedBefore != null && revokedBefore.getEpochSecond() > issuedAtEpochSecond;
  }

  public void invalidateCache(UUID userId) {
    cache.remove(userId);
  }

  private Instant resolveRevokedBefore(UUID userId) {
    Instant now = Instant.now();
    CacheEntry entry = cache.get(userId);
    if (entry != null && entry.cachedAt().isAfter(now.minus(CACHE_TTL))) {
      return entry.revokedBefore();
    }
    Instant revokedBefore = usuarioRepository.findById(userId).map(Usuario::getTokensRevokedBefore).orElse(null);
    cache.put(userId, new CacheEntry(revokedBefore, now));
    return revokedBefore;
  }
}
