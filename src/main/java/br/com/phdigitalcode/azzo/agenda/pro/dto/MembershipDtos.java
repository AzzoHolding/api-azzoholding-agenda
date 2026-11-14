package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Porte verbatim de {@code modules/membership/api/dto/MembershipDtos.java} — mesmos nomes de campo,
 * mesmas anotacoes de validacao, mesmo formato JSON.
 */
public final class MembershipDtos {

  private MembershipDtos() {}

  public static class BenefitRequest {
    @NotNull public String serviceId;

    @Min(1) public int quantidadeMensal;
  }

  public static class PlanRequest {
    @NotBlank public String nome;

    public String descricao;

    @NotNull @DecimalMin("0.01") public BigDecimal precoMensal;

    public boolean cumulativo = false;

    public boolean ativo = true;

    @NotEmpty @Valid public List<BenefitRequest> beneficios = new ArrayList<>();
  }

  public static class BenefitResponse {
    public String serviceId;
    public String serviceNome;
    public int quantidadeMensal;
  }

  public static class PlanResponse {
    public String id;
    public String nome;
    public String descricao;
    public BigDecimal precoMensal;
    public boolean cumulativo;
    public boolean ativo;
    public String createdAt;
    public List<BenefitResponse> beneficios = new ArrayList<>();
  }

  public static class SubscribeRequest {
    @NotNull public String planId;

    /** Exigido pelo Asaas para a primeira cobranca do cliente, se ainda nao configurado. */
    public String customerCpfCnpj;

    /**
     * {@code PIX} (padrao: Pix mensal pelo Asaas, como antes) ou {@code CARTAO}: cartao recorrente
     * pela operadora conectada (Fase 7) — a cliente recebe o link para cadastrar o cartao.
     */
    public String formaDeCobranca;
  }

  public static class BalanceResponse {
    public String serviceId;
    public String serviceNome;
    public int quantidadeMensal;
    public int usadasNoPeriodo;
    public int disponiveis;
  }

  public static class ClientMembershipResponse {
    public String id;
    public String clientId;
    /** Preenchido na lista do salao. */
    public String clientNome;
    public String planId;
    public String planNome;
    public BigDecimal precoMensal;
    public String status;
    public String periodStart;
    public String periodEnd;
    public boolean cancelAtPeriodEnd;
    /** PIX ou CARTAO. */
    public String formaDeCobranca;
    /** Cartao recorrente esperando o cadastro do cartao: o link da operadora para mandar a cliente. */
    public String linkDoCartao;
    public List<BalanceResponse> saldos = new ArrayList<>();
  }
}
