package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Espelha {@code modules/settings/api/dto/TenantLoyaltyDtos.java}. */
public final class TenantLoyaltyDtos {

  private TenantLoyaltyDtos() {}

  public static class UpdateRequest {
    public boolean ativo;

    @NotNull
    @DecimalMin(value = "0.0001", message = "pontos por real precisa ser maior que zero")
    @DecimalMax(value = "1000", message = "no máximo 1.000 pontos por real")
    @Digits(integer = 6, fraction = 4, message = "use até 6 dígitos antes da vírgula e 4 depois")
    public BigDecimal pontosPorReal;

    public boolean produtosContam;

    /** Vazio = os pontos nao vencem. Definida pelo dono do salao. */
    @Min(value = 1, message = "a validade é de pelo menos 1 dia (deixe vazio para não vencer)")
    @Max(value = 3650, message = "a validade é de no máximo 3.650 dias (10 anos)")
    public Integer validadeDias;

    @NotNull
    @DecimalMin(value = "0.0001", message = "pontos para R$ 1,00 precisa ser maior que zero")
    @DecimalMax(value = "999999.9999", message = "no máximo 999.999 pontos para R$ 1,00")
    @Digits(integer = 6, fraction = 4, message = "use até 6 dígitos antes da vírgula e 4 depois")
    public BigDecimal pontosPorResgateReal;
  }

  public static class ConfigResponse {
    public boolean ativo;
    public BigDecimal pontosPorReal;
    public boolean produtosContam;
    public Integer validadeDias;
    public BigDecimal pontosPorResgateReal;
  }

  /** Os pontos da cliente, para a ficha (relatorio de 14/11, Critico 3). */
  public static class ClientLoyaltyResponse {
    /** Pode ser negativo: estorno aceito pelo dono depois que a cliente gastou os pontos. */
    public int saldo;
    public java.math.BigDecimal valorEmReais;
    public boolean programaAtivo;
    /** Validade definida pelo dono do salao; vazio = nao vence. */
    public Integer validadeDias;
    public java.util.List<Vencimento> aVencer = new java.util.ArrayList<>();
    public java.util.List<Movimento> extrato = new java.util.ArrayList<>();
  }

  public static class Vencimento {
    public int pontos;
    public String expiraEm;
  }

  public static class Movimento {
    /** SALDO_INICIAL, CREDITO, RESGATE, DEVOLUCAO, ESTORNO, EXPIRACAO, AJUSTE. */
    public String tipo;
    public int pontos;
    public int saldoApos;
    public String comandaId;
    public String motivo;
    public String expiraEm;
    public String createdAt;
  }
}
