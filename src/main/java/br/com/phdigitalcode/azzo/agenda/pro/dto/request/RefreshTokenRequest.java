package br.com.phdigitalcode.azzo.agenda.pro.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Espelha {@code modules/auth/api/dto/RefreshTokenRequest.java}.
 *
 * <p>NOTA: no Quarkus original o endpoint de refresh nao aplica {@code @Valid} sobre este DTO
 * (o refresh normalmente vem do cookie {@code AZZO_REFRESH_TOKEN}; o corpo e so um fallback), por
 * isso a anotacao {@code @NotBlank} abaixo e preservada por fidelidade mas nao e imposta pelo
 * controller. Ver {@code AuthController#refresh}.
 */
public class RefreshTokenRequest {
  @NotBlank
  public String refresh_token;
}
