package br.com.phdigitalcode.azzo.agenda.pro.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Espelha {@code modules/reports/api/dto/RelatoriosReportsDtos.java}. */
public class RelatoriosReportsDtos {

  // ─── Estoque ─────────────────────────────────────────────────────────────

  public static class EstoqueReportResponse {
    public EstoqueReportSummary summary;
    public List<EstoqueMovimentacaoItem> items;
  }

  public static class EstoqueReportSummary {
    public int totalEntradas;
    public int totalSaidas;
    public int totalAjustes;
    public BigDecimal valorTotalEntradas;
    public BigDecimal valorTotalSaidas;
    public int itensAbaixoMinimo;
  }

  public static class EstoqueMovimentacaoItem {
    public String id;
    public String tipo;
    public String itemId;
    public String itemNome;
    public String unidadeMedida;
    public BigDecimal quantidade;
    public BigDecimal saldoAnterior;
    public BigDecimal saldoPosterior;
    public String motivo;
    public String origem;
    public BigDecimal valorUnitarioPago;
    public BigDecimal valorTotalMovimentacao;
    public String createdAt;
  }

  // ─── Vendas ──────────────────────────────────────────────────────────────

  public static class VendasReportResponse {
    public VendasReportSummary summary;
    public List<VendasServicoItem> topServicos;
    public List<VendasProfissionalItem> topProfissionais;
  }

  public static class VendasReportSummary {
    public int totalAgendamentos;
    public BigDecimal receitaTotal;
    public BigDecimal ticketMedio;
  }

  public static class VendasServicoItem {
    public String servicoId;
    public String servicoNome;
    public int totalAgendamentos;
    public BigDecimal receitaTotal;
    public BigDecimal ticketMedio;
  }

  public static class VendasProfissionalItem {
    public String profissionalId;
    public String profissionalNome;
    public int totalAgendamentos;
    public BigDecimal receitaTotal;
    public BigDecimal ticketMedio;
  }

  // ─── Clientes ────────────────────────────────────────────────────────────

  public static class ClientesReportResponse {
    public ClientesReportSummary summary;
    public List<ClienteReportItem> items;
  }

  public static class ClientesReportSummary {
    public int totalClientes;
    public int clientesAtivos;
    public int clientesInativos;
    public BigDecimal receitaTotal;
  }

  public static class ClienteReportItem {
    public String clienteId;
    public String clienteNome;
    public String clientePhone;
    public String clienteEmail;
    public int totalVisitas;
    public BigDecimal receitaTotal;
    public String ultimaVisita;
    public int diasSemVisita;
    public boolean inativo;
  }

  // ─── Gerencial ───────────────────────────────────────────────────────────

  public static class GerencialReportResponse {
    public GerencialFinanceiroSummary financeiro;
    public GerencialAgendamentosSummary agendamentos;
    public List<VendasServicoItem> topServicos;
    public List<VendasProfissionalItem> topProfissionais;
    public List<DailySeriesItem> serieDiaria;
    /** Taxa de ocupacao em percentual (0-100). Null quando nao ha profissionais ativos no periodo. */
    public Double occupancyRate;
  }

  public static class GerencialFinanceiroSummary {
    public BigDecimal receitaTotal;
    public BigDecimal despesaTotal;
    public BigDecimal saldo;
  }

  public static class GerencialAgendamentosSummary {
    public int totalAgendamentos;
    public int agendamentosConcluidos;
    public int agendamentosCancelados;
    public BigDecimal ticketMedio;
  }

  public static class DailySeriesItem {
    public String date;
    public BigDecimal receita;
    public BigDecimal despesa;
  }

  // ─── Heatmap de ocupacao (F16) ─────────────────────────────────────────────

  public static class HeatmapReportResponse {
    public String dataInicio;
    public String dataFim;
    public String professionalId;
    /** matrix[diaSemana][hora] — dia 0=domingo. Celula null quando nao ha minutos disponiveis (fora do expediente). */
    public List<List<HeatmapCell>> matrix;
  }

  public static class HeatmapCell {
    public int diaSemana;
    public int hora;
    public int agendamentos;
    public int minutosOcupados;
    public int minutosDisponiveis;
    public BigDecimal ocupacaoPercent;
  }

  // ─── Catalogo avancado de relatorios (F18) ──────────────────────────────────

  public static class CatalogReportResponse {
    public String reportKey;
    public String title;
    public List<String> dependencies;
    public List<String> columns;
    public List<java.util.Map<String, Object>> rows;
    public int pageIndex;
    public int pageSize;
    public boolean hasMore;
  }
}
