package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Espelha {@code modules/inventory/api/dto/EstoqueDtos.java}.
 *
 * <p>Cobre as tres fronteiras do modulo: itens/movimentacoes/dashboard/configuracao/insumo,
 * inventario, fornecedor, pedido de compra, transferencia e importacao em massa.
 *
 * <p>Campos publicos porque o original tambem os expoe assim e o contrato JSON precisa bater campo
 * a campo com o que o frontend ja consome.
 */
public final class EstoqueDtos {

  private EstoqueDtos() {}

  public static class ItemEstoqueRequest {
    @NotBlank public String nome;
    public String sku;
    @NotBlank public String unidadeMedida;
    @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) public BigDecimal estoqueMinimo;
    public Boolean ativo;
  }

  public static class ItemEstoqueUpdateRequest {
    public String nome;
    public String sku;
    public String unidadeMedida;
    @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) public BigDecimal estoqueMinimo;
    public Boolean ativo;
  }

  public static class ItemEstoqueResponse {
    public String id;
    public String nome;
    public String sku;
    public String unidadeMedida;
    public BigDecimal saldoAtual;
    public BigDecimal estoqueMinimo;
    public BigDecimal custoMedioUnitario;
    public Boolean ativo;
    public String createdAt;
    public String updatedAt;
  }

  /**
   * {@code quantidade} aceita zero por causa do {@code AJUSTE}: la o numero e o saldo final, e zerar
   * um item e um ajuste legitimo. Para {@code ENTRADA}/{@code SAIDA} o service exige maior que zero.
   */
  public static class MovimentacaoEstoqueRequest {
    @NotBlank public String itemEstoqueId;
    @NotBlank public String tipo;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    public BigDecimal quantidade;

    @NotBlank public String motivo;
    public String origem;

    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    public BigDecimal valorUnitarioPago;

    public Boolean gerarLancamentoFinanceiro;

    /**
     * Forma de pagamento da despesa criada quando {@code gerarLancamentoFinanceiro} esta ligado —
     * {@code CASH}, {@code CREDIT_CARD}, {@code DEBIT_CARD}, {@code PIX} ou {@code OTHER} (padrao).
     */
    public String formaPagamento;
  }

  public static class MovimentacaoEstoqueResponse {
    public String id;
    public String itemEstoqueId;
    public String itemNome;
    public String tipo;
    public BigDecimal quantidade;
    public BigDecimal saldoAnterior;
    public BigDecimal saldoPosterior;
    public String motivo;
    public String origem;
    public BigDecimal valorUnitarioPago;
    public BigDecimal valorTotalMovimentacao;
    public Boolean gerarLancamentoFinanceiro;
    public String transacaoFinanceiraId;
    public String usuarioId;
    public String appointmentId;
    public String createdAt;
  }

  // ─── Insumos de servico ──────────────────────────────────────────────────

  public static class ServicoInsumoRequest {
    @NotBlank public String serviceId;
    @NotBlank public String itemEstoqueId;

    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    public BigDecimal quantidadeConsumo;

    @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2)
    public BigDecimal percentualPerda;
  }

  public static class ServicoInsumoUpdateRequest {
    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    @Digits(integer = 15, fraction = 4)
    public BigDecimal quantidadeConsumo;

    @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2)
    public BigDecimal percentualPerda;
  }

  public static class ServicoInsumoResponse {
    public String id;
    public String serviceId;
    public String itemEstoqueId;
    public String itemNome;
    public String itemUnidadeMedida;
    public BigDecimal saldoAtualItem;
    public BigDecimal quantidadeConsumo;
    public BigDecimal percentualPerda;
    public Boolean ativo;
    public String createdAt;
    public String updatedAt;
  }

  // ─── Dashboard ───────────────────────────────────────────────────────────

  public static class DashboardMargemServicoResponse {
    public String serviceId;
    public long receitaTotal;
    public long custoInsumosTotal;
    public long margemBruta;
  }

  /**
   * {@code perdasValor} e {@code margemServicos} sao do periodo {@code periodoInicio}..
   * {@code periodoFim} (padrao: do dia 1 do mes ate hoje). Os contadores de itens sao a foto de
   * agora — saldo nao tem periodo.
   */
  public static class DashboardEstoqueResponse {
    public String atualizadoEm;
    public String periodoInicio;
    public String periodoFim;
    public int itensAbaixoMinimo;
    public int itensZerados;
    public BigDecimal valorEstoqueCustoMedio;
    public double rupturaTaxa;
    public BigDecimal perdasValor;
    public List<DashboardMargemServicoResponse> margemServicos = new ArrayList<>();
  }

  // ─── Configuracao ────────────────────────────────────────────────────────

  public static class ConfiguracaoEstoqueRequest {
    public Boolean alertaEstoqueMinimoAtivo;
    public Boolean bloquearSaidaSemSaldo;
    public Boolean permitirAjusteNegativoComPermissao;
    @DecimalMin("1.0") public Integer diasCoberturaMeta;
  }

  public static class ConfiguracaoEstoqueResponse {
    public Boolean alertaEstoqueMinimoAtivo;
    public Boolean bloquearSaidaSemSaldo;
    public Boolean permitirAjusteNegativoComPermissao;
    public Integer diasCoberturaMeta;
    public String updatedAt;
  }

  // ─── Importacao em massa ─────────────────────────────────────────────────

  public static class ImportacaoEstoqueJobResponse {
    public String jobId;
    public String tipoImportacao;
    public String status;
    public Boolean dryRun;
    public int totalLinhas;
    public int linhasProcessadas;
    public int linhasComErro;
    public String arquivoSha256;
    public String arquivoStorageKey;
    public String createdAt;
    public String updatedAt;
    public String finishedAt;
  }

  public static class ImportacaoErroLinhaResponse {
    public int linha;
    public String coluna;
    public String codigoErro;
    public String mensagem;
    public String valorRecebido;
  }

  public static class ImportacaoResultadoArquivoResponse {
    public String downloadUrl;
    public String expiresAt;
  }

  // ─── Inventario / contagem ───────────────────────────────────────────────

  public static class InventarioEstoqueRequest {
    @NotBlank public String nome;
    public String observacao;
  }

  public static class InventarioContagemRequest {
    @NotBlank public String itemEstoqueId;

    @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4)
    public BigDecimal quantidadeContada;

    public String observacao;
  }

  public static class AtualizarContagemInventarioRequest {
    @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4)
    public BigDecimal quantidadeContada;

    public String observacao;
  }

  /** {@code motivo} e opcional; entra so no payload de auditoria do cancelamento. */
  public static class CancelarInventarioRequest {
    @NotBlank public String senha;
    public String motivo;
  }

  public static class InventarioContagemResponse {
    public String id;
    public String inventarioId;
    public String itemEstoqueId;
    public String itemNome;
    public String itemUnidadeMedida;
    public BigDecimal quantidadeEsperada;
    public BigDecimal quantidadeContada;
    public BigDecimal diferenca;
    public String observacao;
    public String usuarioId;
    public String usuarioAtualizacaoId;
    public String createdAt;
    public String updatedAt;
  }

  public static class InventarioEstoqueResponse {
    public String id;
    public String nome;
    public String status;
    public String observacao;
    public String dataAbertura;
    public String dataFechamento;
    public String createdAt;
    public String updatedAt;
  }

  /** Unica listagem paginada do recurso que devolve envelope em vez de lista crua. */
  public static class InventarioEstoquePageResponse {
    public List<InventarioEstoqueResponse> items;
    public int page;
    public int totalPages;
    public long total;
    public boolean hasNext;
  }

  // ─── Fornecedor ──────────────────────────────────────────────────────────

  public static class FornecedorEstoqueRequest {
    @NotBlank public String nome;
    public String documento;
    public String email;
    public String telefone;
    public String contato;
    public Boolean ativo;
  }

  public static class FornecedorEstoqueResponse {
    public String id;
    public String nome;
    public String documento;
    public String email;
    public String telefone;
    public String contato;
    public Boolean ativo;
    public String createdAt;
    public String updatedAt;
  }

  // ─── Pedido de compra ────────────────────────────────────────────────────

  public static class PedidoCompraEstoqueRequest {
    @NotBlank public String fornecedorId;
    @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) public BigDecimal valorTotal;
    @NotNull public Integer quantidadeItens;
    public String observacao;
  }

  /**
   * {@code itemEstoqueId} e opcional: o pedido de compra nao sabe de que item e (so fornecedor,
   * valor e quantos itens). Informado, o recebimento da <b>entrada</b> nesse item — o que antes
   * exigia lancar a entrada de novo, a mao. {@code quantidadeEstoque} e quanto entra no saldo, na
   * unidade do item (dez frascos de 1 L em um item medido em ML sao 10000); ausente, vale a
   * {@code quantidadeRecebida}.
   */
  public static class PedidoCompraRecebimentoRequest {
    @NotNull public Integer quantidadeRecebida;
    public String observacao;
    public String itemEstoqueId;

    @DecimalMin(value = "0.0001", inclusive = true) @Digits(integer = 15, fraction = 4)
    public BigDecimal quantidadeEstoque;
  }

  public static class PedidoCompraEstoqueResponse {
    public String id;
    public String fornecedorId;
    public String fornecedorNome;
    public String status;
    public BigDecimal valorTotal;
    public Integer quantidadeItens;
    public Integer quantidadePendente;
    public String observacao;
    public String createdAt;
    public String updatedAt;
  }

  // ─── Transferencia ───────────────────────────────────────────────────────

  public static class TransferenciaEstoqueRequest {
    @NotBlank public String origem;
    @NotBlank public String destino;
    @NotBlank public String itemEstoqueId;
    @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) public BigDecimal quantidade;
    public String observacao;
  }

  /** O que a pessoa logada pode fazer no estoque — para a tela nao oferecer o que daria 403. */
  public static class PermissoesEstoqueResponse {
    public boolean podeGerenciar;
  }

  public static class TransferenciaEstoqueResponse {
    public String id;
    public String origem;
    public String destino;
    public String status;
    public String itemEstoqueId;
    public String itemNome;
    public BigDecimal quantidade;
    public String observacao;
    public String createdAt;
    public String updatedAt;
  }
}
