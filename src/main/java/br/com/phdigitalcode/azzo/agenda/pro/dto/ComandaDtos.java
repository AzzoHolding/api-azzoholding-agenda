package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Porte verbatim de {@code modules/pos/api/dto/ComandaDtos.java} — mesmos nomes de campo, mesmas
 * anotacoes de validacao, mesmo formato JSON.
 */
public final class ComandaDtos {

  private ComandaDtos() {}

  public static class AbrirComandaRequest {
    public String appointmentId;
    public String clientId;
  }

  public static class AdicionarItemRequest {
    @NotBlank
    @Pattern(regexp = "SERVICO|PRODUTO|PACOTE")
    public String tipo;

    @NotBlank public String referenciaId;
    public String professionalId;

    @DecimalMin(value = "0.001", inclusive = true)
    public BigDecimal quantidade = BigDecimal.ONE;

    /** Obrigatorio para PRODUTO; para SERVICO usa o preco de tabela se omitido. */
    public BigDecimal precoUnitario;
  }

  public static class AplicarDescontoRequest {
    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    public BigDecimal percentual;

    @NotBlank public String motivo;
  }

  public static class RegistrarGorjetaRequest {
    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    public BigDecimal valor;

    @NotBlank public String professionalId;
  }

  public static class RegistrarPagamentoRequest {
    @NotBlank
    @Pattern(
        regexp = "DINHEIRO|PIX_ASAAS|CARTAO_CREDITO_EXTERNO|CARTAO_DEBITO_EXTERNO|CREDITO_SINAL")
    public String meio;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    public BigDecimal valor;
  }

  public static class CancelarComandaRequest {
    @NotBlank public String motivo;
  }

  public static class EstornarComandaRequest {
    @NotBlank public String motivo;
  }

  public static class ResgatarFidelidadeRequest {
    @Min(1) public int pontos;
  }

  public static class ComandaItemResponse {
    public String id;
    public String tipo;
    public String referenciaId;
    public String descricao;
    public String professionalId;
    public BigDecimal quantidade;
    public BigDecimal precoUnitario;
    public BigDecimal total;
  }

  public static class ComandaPagamentoResponse {
    public String id;
    public String meio;
    public BigDecimal valor;
    public String status;
    public String pixPayload;
    public String paidAt;
  }

  public static class ComandaResponse {
    public String id;
    public String appointmentId;
    public String clientId;
    public String status;
    public BigDecimal subtotal;
    public BigDecimal desconto;
    public String descontoMotivo;
    public BigDecimal gorjeta;
    public String gorjetaProfessionalId;
    public BigDecimal total;
    public String cancelMotivo;
    public String estornoMotivo;
    public String estornadoEm;
    public String openedAt;
    public String closedAt;
    public List<ComandaItemResponse> itens = new ArrayList<>();
    public List<ComandaPagamentoResponse> pagamentos = new ArrayList<>();
  }

  public static class ComandaPageResponse {
    public List<ComandaResponse> content = new ArrayList<>();
    public long totalElements;
    public int page;
    public int size;
  }
}
