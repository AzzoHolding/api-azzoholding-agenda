package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cupons de desconto e o gerador de lotes (V180; item 6, PLANO-CUPONS-VALES.md). */
public final class CupomDtos {

  private CupomDtos() {}

  /** As regras do desconto: iguais no cupom avulso e no lote. */
  public static class RegrasDoDesconto {
    @NotBlank(message = "Escolha o tipo do desconto")
    @Pattern(regexp = "PERCENTUAL|VALOR", message = "O tipo do desconto é PERCENTUAL ou VALOR")
    public String tipo;

    @NotNull(message = "Informe o valor do desconto")
    @DecimalMin(value = "0.01", message = "O desconto precisa ser maior que zero")
    public BigDecimal valor;

    /** yyyy-MM-dd; vazio = vale desde ja. */
    public String inicio;

    /** yyyy-MM-dd; vazio = sem data de fim. */
    public String fim;

    @DecimalMin(value = "0.01", message = "O valor mínimo precisa ser maior que zero")
    public BigDecimal valorMinimo;
  }

  public static class CupomRequest extends RegrasDoDesconto {
    @NotBlank(message = "Informe o código do cupom")
    @Pattern(regexp = "[A-Za-z0-9]{3,20}", message = "O código tem de 3 a 20 letras e números, sem espaço")
    public String codigo;

    @Size(max = 120, message = "A descrição deve ter no máximo 120 caracteres")
    public String descricao;

    @Min(value = 1, message = "O limite de usos precisa ser pelo menos 1")
    public Integer limiteTotal;

    @Min(value = 1, message = "O limite por cliente precisa ser pelo menos 1")
    public Integer limitePorCliente;

    public Boolean ativo;
  }

  public static class CupomResponse {
    public String id;
    public String codigo;
    public String descricao;
    public String tipo;
    public BigDecimal valor;
    public String inicio;
    public String fim;
    public Integer limiteTotal;
    public Integer limitePorCliente;
    public BigDecimal valorMinimo;
    public int usos;
    public boolean ativo;
    /** VALIDO, AGENDADO (inicio futuro), VENCIDO, ESGOTADO, INATIVO. */
    public String situacao;
    public String createdAt;
  }

  public static class UsoResponse {
    public String id;
    public String codigo;
    public String comandaId;
    public String appointmentId;
    public String clientId;
    public String clienteNome;
    public BigDecimal valorDesconto;
    public String status;
    public String createdAt;
  }

  // ─── Gerador ───────────────────────────────────────────────────────────────

  public static class LoteRequest extends RegrasDoDesconto {
    @NotBlank(message = "Dê um nome ao lote")
    @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres")
    public String nome;

    @NotBlank(message = "Informe o prefixo dos códigos")
    @Pattern(regexp = "[A-Za-z0-9]{2,10}", message = "O prefixo tem de 2 a 10 letras e números, sem espaço")
    public String prefixo;

    @NotNull(message = "Informe quantos códigos gerar")
    @Min(value = 1, message = "Gere pelo menos 1 código")
    @Max(value = 1000, message = "Gere no máximo 1.000 códigos por vez")
    public Integer quantidade;
  }

  public static class MaisCodigosRequest {
    @NotNull(message = "Informe quantos códigos gerar")
    @Min(value = 1, message = "Gere pelo menos 1 código")
    @Max(value = 1000, message = "Gere no máximo 1.000 códigos por vez")
    public Integer quantidade;
  }

  public static class LoteResumoResponse {
    public String id;
    public String nome;
    public String prefixo;
    public String tipo;
    public BigDecimal valor;
    public String inicio;
    public String fim;
    public BigDecimal valorMinimo;
    public boolean ativo;
    public int gerados;
    public int usados;
    public int naoUsados;
    public int vencidos;
    public int desativados;
    public String createdAt;
  }

  public static class CodigoDoLoteResponse {
    public String id;
    public String codigo;
    /** USADO, NAO_USADO, VENCIDO, DESATIVADO. */
    public String situacao;
    public String usadoEm;
    public String clientId;
    public String clienteNome;
    public String comandaId;
    public String appointmentId;
    public BigDecimal valorDesconto;
  }

  public static class LoteResponse extends LoteResumoResponse {
    public List<CodigoDoLoteResponse> codigos = new ArrayList<>();
  }

  // ─── Na comanda e no agendamento ───────────────────────────────────────────

  public static class AplicarCupomRequest {
    @NotBlank(message = "Informe o código do cupom")
    @Size(max = 20, message = "Código inválido")
    public String codigo;
  }

  /** O que a pagina publica pergunta antes de agendar. */
  public static class ValidarCupomPublicoRequest {
    @NotBlank(message = "Informe o código do cupom")
    @Size(max = 20, message = "Código inválido")
    public String codigo;

    public List<String> serviceIds = new ArrayList<>();

    public String serviceId;

    /** Para o limite por cliente: o telefone que a cliente vai usar no agendamento. */
    public String telefone;
  }

  public static class ValidarCupomPublicoResponse {
    public boolean valido;
    public BigDecimal desconto;
    public BigDecimal totalComDesconto;
    public String descricao;
    /** Motivo da recusa em frase, sem dizer se o codigo existe. */
    public String mensagem;
  }

  /** O cupom aplicado numa comanda. */
  public static class CupomDaComandaResponse {
    public String codigo;
    public BigDecimal valor;
    public String tipo;
    public BigDecimal percentualOuValor;
    /** true quando veio do agendamento online: o desconto ja esta no preco dos itens. */
    public boolean doAgendamento;
  }
}
