package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Vales-presente (V181; item 6, PLANO-CUPONS-VALES.md). */
public final class ValeDtos {

  private ValeDtos() {}

  public static class ValeResponse {
    public String id;
    public String codigo;
    public BigDecimal valorInicial;
    public BigDecimal saldo;
    public String expiraEm;
    /** ATIVO, ESGOTADO, VENCIDO, CANCELADO. */
    public String status;
    public String compradorClientId;
    public String compradorNome;
    public String destinatarioNome;
    public String mensagem;
    public String comandaVendaId;
    public String createdAt;
    public List<MovimentoResponse> extrato = new ArrayList<>();
  }

  public static class MovimentoResponse {
    public String tipo;
    public BigDecimal valor;
    public BigDecimal saldoApos;
    public String comandaId;
    public String motivo;
    public String createdAt;
  }

  public static class ListaDeValesResponse {
    public List<ValeResponse> vales = new ArrayList<>();
    /** Soma dos saldos ativos: o que o salao deve em servicos. */
    public BigDecimal saldoEmAberto;
  }

  public static class AjustarRequest {
    @NotNull(message = "Informe o novo saldo")
    @DecimalMin(value = "0.00", message = "O saldo não pode ser negativo")
    public BigDecimal saldo;

    @NotBlank(message = "Informe o motivo do ajuste")
    @Size(max = 200, message = "O motivo deve ter no máximo 200 caracteres")
    public String motivo;
  }

  public static class CancelarRequest {
    @NotBlank(message = "Informe o motivo do cancelamento")
    @Size(max = 200, message = "O motivo deve ter no máximo 200 caracteres")
    public String motivo;
  }

  public static class ProrrogarRequest {
    /** yyyy-MM-dd */
    @NotBlank(message = "Informe a nova data de validade")
    public String expiraEm;

    @NotBlank(message = "Informe o motivo")
    @Size(max = 200, message = "O motivo deve ter no máximo 200 caracteres")
    public String motivo;
  }
}
