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
 * Porte verbatim de {@code modules/packages/api/dto/PackageDtos.java} — mesmos nomes de campo,
 * mesmas anotacoes de validacao, mesmo formato JSON.
 */
public final class PackageDtos {

  private PackageDtos() {}

  public static class ItemRequest {
    @NotNull public String serviceId;

    @Min(1) public int sessoes;
  }

  public static class PackageRequest {
    @NotBlank public String nome;

    public String descricao;

    @NotNull @DecimalMin("0.01") public BigDecimal preco;

    public boolean ativo = true;

    @NotEmpty @Valid public List<ItemRequest> itens = new ArrayList<>();
  }

  public static class ItemResponse {
    public String serviceId;
    public String serviceNome;
    public int sessoes;
  }

  public static class PackageResponse {
    public String id;
    public String nome;
    public String descricao;
    public BigDecimal preco;
    public boolean ativo;
    public String createdAt;
    public List<ItemResponse> itens = new ArrayList<>();
  }

  public static class ClientPackageBalanceResponse {
    public String serviceId;
    public String serviceNome;
    public int sessoesTotais;
    public int sessoesUsadas;
    public int sessoesDisponiveis;
  }

  public static class ClientPackagePurchaseResponse {
    public String id;
    public String packageId;
    public String packageNome;
    public BigDecimal precoPago;
    public String createdAt;
    public List<ClientPackageBalanceResponse> saldos = new ArrayList<>();
  }
}
