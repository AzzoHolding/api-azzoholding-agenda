package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
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

    @Min(value = 1, message = "cada serviço precisa de pelo menos 1 sessão")
    @Max(value = 999, message = "no máximo 999 sessões por serviço")
    public int sessoes;
  }

  public static class PackageRequest {
    @NotBlank
    @Size(max = 80, message = "o nome tem no máximo 80 letras")
    public String nome;

    public String descricao;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 8, fraction = 2, message = "o preço tem no máximo 2 casas depois da vírgula")
    public BigDecimal preco;

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
