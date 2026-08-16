package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.UUID;

/**
 * Principal autenticado colocado no {@code SecurityContext} pelo {@link JwtAuthenticationFilter}.
 * Espelha o que o Quarkus obtinha via {@code JsonWebToken} injetado (sub, tid/tenant_id, iat).
 */
public record JwtPrincipal(UUID userId, UUID tenantId, String email, String name, long issuedAtEpochSecond) {

  @Override
  public String toString() {
    return userId != null ? userId.toString() : "anonymous";
  }
}
