package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;

/**
 * Equivalente Spring de {@code modules/security/infrastructure/ProvedorJwt.java} +
 * validacao correspondente ao {@code quarkus-smallrye-jwt} (MicroProfile JWT).
 *
 * <p>Preserva exatamente o formato de claims do Quarkus original: {@code iss=azzo-agenda-pro},
 * {@code upn=email}, {@code sub=userId}, {@code groups=[ROLE]}, {@code tid}/{@code tenant_id}
 * (redundante de proposito), {@code name}, {@code iat}/{@code exp}. Assinatura RSA
 * (RS256), TTL configuravel via {@code app.security.access-token.ttl-minutes} (minimo 5 min,
 * mesma regra do original).
 */
@Service
public class JwtService {

  private static final String ISSUER = "azzo-agenda-pro";

  @Value("${app.security.access-token.ttl-minutes:15}")
  private int accessTokenTtlMinutes;

  @Value("${app.security.jwt.private-key-location:${app.security.jwt.private-key:}}")
  private String privateKeyLocation;

  @Value("${app.security.jwt.public-key-location:${app.security.jwt.public-key:}}")
  private String publicKeyLocation;

  private PrivateKey privateKey;
  private PublicKey publicKey;

  @PostConstruct
  void init() {
    this.privateKey = PemKeyUtils.loadPrivateKey(privateKeyLocation);
    this.publicKey = PemKeyUtils.loadPublicKey(publicKeyLocation);
  }

  public String gerarToken(Usuario usuario) {
    Instant agora = Instant.now();
    Duration ttl = Duration.ofMinutes(Math.max(5, accessTokenTtlMinutes));
    return Jwts.builder()
        .issuer(ISSUER)
        .claim("upn", usuario.getEmail())
        .subject(usuario.getId().toString())
        .claim("groups", List.of(usuario.getRole().name()))
        .claim("tid", usuario.getTenantId().toString())
        .claim("tenant_id", usuario.getTenantId().toString())
        .claim("name", usuario.getName())
        .issuedAt(Date.from(agora))
        .expiration(Date.from(agora.plus(ttl)))
        .signWith(privateKey)
        .compact();
  }

  public long accessTokenExpiresInSeconds() {
    return Duration.ofMinutes(Math.max(5, accessTokenTtlMinutes)).toSeconds();
  }

  /** Retorna as claims validas (assinatura + expiracao + issuer), ou lanca JwtException. */
  public Claims parseAndValidate(String token) {
    Claims claims = Jwts.parser()
        .verifyWith(publicKey)
        .requireIssuer(ISSUER)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    if (claims.getSubject() == null || claims.getSubject().isBlank()) {
      throw new JwtException("Token sem subject (sub)");
    }
    return claims;
  }

  @SuppressWarnings("unchecked")
  public Set<String> extractRoles(Claims claims) {
    Object groups = claims.get("groups");
    if (groups instanceof List<?> list) {
      return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toSet());
    }
    return Set.of();
  }

  public UUID extractTenantId(Claims claims) {
    Object tid = claims.get("tenant_id");
    if (tid == null || tid.toString().isBlank()) {
      tid = claims.get("tid");
    }
    if (tid == null || tid.toString().isBlank()) return null;
    return UUID.fromString(tid.toString());
  }
}
