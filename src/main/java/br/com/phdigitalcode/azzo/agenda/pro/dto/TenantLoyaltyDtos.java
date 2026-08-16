package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Espelha {@code modules/settings/api/dto/TenantLoyaltyDtos.java}. */
public final class TenantLoyaltyDtos {

  private TenantLoyaltyDtos() {}

  public static class UpdateRequest {
    public boolean ativo;

    @NotNull
    @DecimalMin("0.0001")
    public BigDecimal pontosPorReal;

    public boolean produtosContam;

    public Integer validadeDias;

    @NotNull
    @DecimalMin("0.0001")
    public BigDecimal pontosPorResgateReal;
  }

  public static class ConfigResponse {
    public boolean ativo;
    public BigDecimal pontosPorReal;
    public boolean produtosContam;
    public Integer validadeDias;
    public BigDecimal pontosPorResgateReal;
  }

  public static class ClientLoyaltyResponse {
    public int saldo;
  }
}
