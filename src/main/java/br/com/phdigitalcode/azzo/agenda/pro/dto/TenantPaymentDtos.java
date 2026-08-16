package br.com.phdigitalcode.azzo.agenda.pro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Espelha {@code modules/settings/api/dto/TenantPaymentDtos.java}. */
public final class TenantPaymentDtos {

  private TenantPaymentDtos() {}

  public static class UpdateRequest {
    @NotBlank public String apiKey;

    @NotBlank
    @Pattern(regexp = "SANDBOX|PRODUCAO")
    public String ambiente;
  }

  public static class ConfigResponse {
    public String provider;
    public String ambiente;
    public boolean ativo;
    public String apiKeyMascarada;
    public String webhookPath;
  }
}
