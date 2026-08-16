package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.time.Instant;

/** Espelha {@code TenantToggleDtos.ToggleResponse}. */
public class ToggleResponse {
  public String key;
  public Object value;
  public Instant updatedAt;

  public ToggleResponse(String key, Object value) {
    this.key = key;
    this.value = value;
    this.updatedAt = Instant.now();
  }
}
