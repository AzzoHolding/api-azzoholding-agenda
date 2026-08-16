package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
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
    @NotNull @DecimalMin("0.0") public BigDecimal estoqueMinimo;
    public Boolean ativo;
  }

  public static class ItemEstoqueUpdateRequest {
    public String nome;
    public String sku;
    public String unidadeMedida;
    @DecimalMin("0.0") public BigDecimal estoqueMinimo;
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

  public static class MovimentacaoEstoqueRequest {
    @NotBlank public String itemEstoqueId;
    @NotBlank public String tipo;
    @NotNull @DecimalMin(value = "0.0001", inclusive = true) public BigDecimal quantidade;
    @NotBlank public String motivo;
    public String origem;
    public BigDecimal valorUnitarioPago;
    public Boolean gerarLancamentoFinanceiro;
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
    @NotNull @DecimalMin(value = "0.0001", inclusive = true) public BigDecimal quantidadeConsumo;
    @DecimalMin("0.0") public BigDecimal percentualPerda;
  }

  public static class ServicoInsumoUpdateRequest {
    @NotNull @DecimalMin(value = "0.0001", inclusive = true) public BigDecimal quantidadeConsumo;
    @DecimalMin("0.0") public BigDecimal percentualPerda;
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

  public static class DashboardEstoqueResponse {
    public String atualizadoEm;
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
    @NotNull @DecimalMin("0.0") public BigDecimal quantidadeContada;
    public String observacao;
  }

  public static class AtualizarContagemInventarioRequest {
    @NotNull @DecimalMin("0.0") public BigDecimal quantidadeContada;
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
    @NotNull @DecimalMin("0.0") public BigDecimal valorTotal;
    @NotNull public Integer quantidadeItens;
    public String observacao;
  }

  public static class PedidoCompraRecebimentoRequest {
    @NotNull public Integer quantidadeRecebida;
    public String observacao;
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
    @NotNull @DecimalMin(value = "0.0001") public BigDecimal quantidade;
    public String observacao;
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
