package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import br.com.phdigitalcode.azzo.agenda.pro.dto.request.AtualizarCategoriaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.CriarCategoriaRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.RecurringTransactionRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.request.TransacaoRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.CategoriaResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.FluxoCaixaDiarioResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.PagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.RecurringTransactionResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ResumoFinanceiroResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.TransacaoResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFinanceiro;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/finance/api/FinanceiroResource.java} — mesmos paths, metodos, parametros
 * de query, status codes e nomes de campo. Toda a normalizacao "tolerante" dos filtros (valor
 * invalido vira {@code null} em vez de erro 400) foi preservada exatamente como no original.
 */
@RestController
@RequestMapping("/api/v1/finance/transactions")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class FinanceiroController {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  private final ServicoFinanceiro servicoFinanceiro;

  public FinanceiroController(ServicoFinanceiro servicoFinanceiro) {
    this.servicoFinanceiro = servicoFinanceiro;
  }

  // ─── TRANSACOES ──────────────────────────────────────────────────────────

  @GetMapping
  @RequiresPermission("finance:view")
  public PagedResponse<TransacaoResponse> listar(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String paymentMethod,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) String reconciled,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int limit) {
    int safeLimit = Math.min(Math.max(1, limit), 200);
    return servicoFinanceiro.listar(
        parseFilterStartInstantNullable(from),
        parseFilterEndInstantNullable(to),
        parseEnumNullable(type, TipoTransacao.class),
        parseUuidNullable(categoryId),
        parseEnumNullable(paymentMethod, MetodoPagamento.class),
        parseUuidNullable(professionalId),
        parseBooleanNullable(reconciled),
        Math.max(0, page),
        safeLimit);
  }

  @GetMapping("/summary")
  @RequiresPermission("finance:view")
  public ResumoFinanceiroResponse resumo(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String paymentMethod,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) String reconciled) {
    return servicoFinanceiro.resumo(
        parseFilterStartInstantNullable(from),
        parseFilterEndInstantNullable(to),
        parseEnumNullable(type, TipoTransacao.class),
        parseUuidNullable(categoryId),
        parseEnumNullable(paymentMethod, MetodoPagamento.class),
        parseUuidNullable(professionalId),
        parseBooleanNullable(reconciled));
  }

  @PostMapping
  @RequiresPermission("finance:manage")
  public TransacaoResponse criar(@Valid @RequestBody TransacaoRequest request) {
    return servicoFinanceiro.criar(request);
  }

  @PutMapping("/{id}")
  @RequiresPermission("finance:manage")
  public TransacaoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody TransacaoRequest request) {
    return servicoFinanceiro.atualizar(id, request);
  }

  @PatchMapping("/{id}/reconcile")
  @RequiresPermission("finance:manage")
  public TransacaoResponse conciliar(@PathVariable UUID id) {
    return servicoFinanceiro.conciliar(id);
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("finance:manage")
  public void deletar(@PathVariable UUID id) {
    servicoFinanceiro.deletar(id);
  }

  // ─── EXPORTACAO CSV (F1.2) ───────────────────────────────────────────────

  @GetMapping(value = "/export", produces = "text/csv")
  @RequiresPermission("finance:view")
  public ResponseEntity<StreamingResponseBody> exportarCsv(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String categoryId,
      @RequestParam(required = false) String paymentMethod,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) String reconciled) {
    StreamingResponseBody stream =
        servicoFinanceiro.exportarCsv(
            parseFilterStartInstantNullable(from),
            parseFilterEndInstantNullable(to),
            parseEnumNullable(type, TipoTransacao.class),
            parseUuidNullable(categoryId),
            parseEnumNullable(paymentMethod, MetodoPagamento.class),
            parseUuidNullable(professionalId),
            parseBooleanNullable(reconciled));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + buildCsvFilename(from, to) + "\"")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
        .body(stream);
  }

  // ─── FLUXO DE CAIXA (F1.4) ───────────────────────────────────────────────

  @GetMapping("/cash-flow")
  @RequiresPermission("finance:view")
  public List<FluxoCaixaDiarioResponse> fluxoDeCaixa(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    LocalDate dateFrom = parseLocalDateNullable(from, LocalDate.now().withDayOfMonth(1));
    LocalDate dateTo = parseLocalDateNullable(to, LocalDate.now());
    return servicoFinanceiro.fluxoDeCaixa(dateFrom, dateTo);
  }

  // ─── CATEGORIAS ──────────────────────────────────────────────────────────

  @GetMapping("/categories")
  @RequiresPermission("finance:view")
  public List<CategoriaResponse> listarCategorias() {
    return servicoFinanceiro.listarCategorias();
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("finance:manage")
  public CategoriaResponse criarCategoria(@Valid @RequestBody CriarCategoriaRequest req) {
    return servicoFinanceiro.criarCategoria(req.name);
  }

  @PutMapping("/categories/{id}")
  @RequiresPermission("finance:manage")
  public CategoriaResponse renomearCategoria(
      @PathVariable UUID id, @Valid @RequestBody AtualizarCategoriaRequest req) {
    return servicoFinanceiro.renomearCategoria(id, req.name);
  }

  @DeleteMapping("/categories/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission("finance:manage")
  public void excluirCategoria(@PathVariable UUID id) {
    servicoFinanceiro.excluirCategoria(id);
  }

  // ─── RECORRENTES (F2.2) ──────────────────────────────────────────────────

  @GetMapping("/recurring")
  @RequiresPermission("finance:view")
  public List<RecurringTransactionResponse> listarRecorrentes() {
    return servicoFinanceiro.listarRecorrentes();
  }

  @PostMapping("/recurring")
  @ResponseStatus(HttpStatus.CREATED)
  @RequiresPermission("finance:manage")
  public RecurringTransactionResponse criarRecorrente(
      @Valid @RequestBody RecurringTransactionRequest req) {
    return servicoFinanceiro.criarRecorrente(req);
  }

  @DeleteMapping("/recurring/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @RequiresPermission("finance:manage")
  public void desativarRecorrente(@PathVariable UUID id) {
    servicoFinanceiro.desativarRecorrente(id);
  }

  // ─── UTILITARIOS PRIVADOS ────────────────────────────────────────────────

  private Instant parseFilterStartInstantNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      if (isDateOnlyValue(value)) {
        return DataUtil.parseDataISO(value).atStartOfDay(ZONA_BR).toInstant();
      }
      return Instant.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  private Instant parseFilterEndInstantNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      if (isDateOnlyValue(value)) {
        return DataUtil.parseDataISO(value).plusDays(1).atStartOfDay(ZONA_BR).minusNanos(1).toInstant();
      }
      return Instant.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  private boolean isDateOnlyValue(String value) {
    return value != null && value.trim().length() <= 10;
  }

  private Boolean parseBooleanNullable(String value) {
    return value == null || value.isBlank() ? null : Boolean.parseBoolean(value);
  }

  private UUID parseUuidNullable(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (Exception e) {
      return null;
    }
  }

  private <E extends Enum<E>> E parseEnumNullable(String value, Class<E> enumClass) {
    if (value == null || value.isBlank()) return null;
    try {
      return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      return null;
    }
  }

  private LocalDate parseLocalDateNullable(String value, LocalDate fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      // Aceita ISO instant (2026-03-20T...) ou data simples (2026-03-20)
      return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    } catch (Exception e) {
      return fallback;
    }
  }

  private String buildCsvFilename(String from, String to) {
    String f = (from != null && from.length() >= 7) ? from.substring(0, 7) : "all";
    String t = (to != null && to.length() >= 7) ? to.substring(0, 7) : "all";
    return "lancamentos-" + f + "-a-" + t + ".csv";
  }
}
