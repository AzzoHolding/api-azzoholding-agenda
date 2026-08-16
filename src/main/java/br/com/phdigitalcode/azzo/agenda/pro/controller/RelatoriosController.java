package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatorioComissaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatorioDiarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RelatoriosReportsDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoCatalogoRelatorios;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoRelatorios;

/**
 * Espelha {@code modules/reports/api/RelatoriosResource.java} (rotas {@code /api/v1/reports/*}).
 *
 * <p><b>Endpoint nao portado nesta etapa</b>: {@code GET /abandonment} — delega para
 * {@code ServicoRelatorios.abandono}, que por sua vez delega para
 * {@code ServicoDashboard.listarFilaReativacaoWhatsApp} (modulo {@code chat}, ainda nao portado).
 *
 * <p>As rotas de catalogo ({@code /{nome}} e {@code /{nome}/export}) sao registradas por ultimo no
 * original "para nao sombrear as rotas literais acima" — comentario preservado; no Spring MVC,
 * assim como no JAX-RS, {@code @GetMapping} resolve paths literais antes de {@code {nome}}
 * independente da ordem de declaracao, mas a ordem de metodos no arquivo segue a mesma
 * organizacao do original por clareza.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class RelatoriosController {

  private final ServicoRelatorios servicoRelatorios;
  private final ServicoCatalogoRelatorios servicoCatalogoRelatorios;

  public RelatoriosController(ServicoRelatorios servicoRelatorios, ServicoCatalogoRelatorios servicoCatalogoRelatorios) {
    this.servicoRelatorios = servicoRelatorios;
    this.servicoCatalogoRelatorios = servicoCatalogoRelatorios;
  }

  @GetMapping("/daily")
  @RequiresPermission("finance:view")
  public RelatorioDiarioResponse diario(@RequestParam String date) {
    return servicoRelatorios.diario(date);
  }

  @GetMapping("/heatmap")
  @RequiresPermission("finance:view")
  public RelatoriosReportsDtos.HeatmapReportResponse heatmap(
      @RequestParam String dataInicio, @RequestParam String dataFim, @RequestParam(required = false) String professionalId) {
    return servicoRelatorios.heatmap(dataInicio, dataFim, professionalId);
  }

  @GetMapping("/commissions")
  @RequiresPermission("finance:view")
  public RelatorioComissaoResponse comissoes(
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) String professionalUserId) {
    return servicoRelatorios.comissoes(from, to, professionalId, professionalUserId);
  }

  @GetMapping("/estoque")
  @RequiresPermission("finance:view")
  public RelatoriosReportsDtos.EstoqueReportResponse relatorioEstoque(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String itemId,
      @RequestParam(required = false) String tipo) {
    return servicoRelatorios.relatorioEstoque(from, to, itemId, tipo);
  }

  @GetMapping("/vendas")
  @RequiresPermission("finance:view")
  public RelatoriosReportsDtos.VendasReportResponse relatorioVendas(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String professionalId) {
    return servicoRelatorios.relatorioVendas(from, to, professionalId);
  }

  @GetMapping("/clientes")
  @RequiresPermission("finance:view")
  public RelatoriosReportsDtos.ClientesReportResponse relatorioClientes(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false, defaultValue = "false") boolean onlyInactive) {
    return servicoRelatorios.relatorioClientes(from, to, onlyInactive);
  }

  @GetMapping("/gerencial")
  @RequiresPermission("finance:view")
  public RelatoriosReportsDtos.GerencialReportResponse relatorioGerencial(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    return servicoRelatorios.relatorioGerencial(from, to);
  }

  // ─── Catalogo avancado de relatorios (F18) ────────────────────────────────

  @GetMapping("/{nome}")
  @RequiresPermission("finance:view")
  public RelatoriosReportsDtos.CatalogReportResponse catalogo(
      @PathVariable String nome,
      @RequestParam(required = false) String dataInicio,
      @RequestParam(required = false) String dataFim,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) Integer inactiveDays,
      @RequestParam(required = false) Integer pageIndex,
      @RequestParam(required = false) Integer pageSize) {
    return servicoCatalogoRelatorios.gerar(nome, dataInicio, dataFim, professionalId, inactiveDays, pageIndex, pageSize);
  }

  @GetMapping("/{nome}/export")
  @RequiresPermission("finance:view")
  public ResponseEntity<byte[]> exportarCatalogo(
      @PathVariable String nome,
      @RequestParam(required = false) String dataInicio,
      @RequestParam(required = false) String dataFim,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) Integer inactiveDays,
      @RequestParam(required = false, defaultValue = "csv") String format) {
    ServicoCatalogoRelatorios.ExportedReport export =
        servicoCatalogoRelatorios.exportar(nome, dataInicio, dataFim, professionalId, format, inactiveDays);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(export.mediaType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.fileName() + "\"")
        .header("X-Export-Row-Count", String.valueOf(export.rowsCount()))
        .header("X-Export-Format", export.format())
        .body(export.content());
  }
}
