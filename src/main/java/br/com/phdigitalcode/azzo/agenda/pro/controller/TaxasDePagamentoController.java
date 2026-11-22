package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.PagamentosDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeTaxasDePagamento;

/**
 * Pagamentos integrados — Fase 5: taxas da operadora por conta (so o dono) e recebiveis (quem ve o
 * financeiro).
 */
@RestController
@RequestMapping("/api/v1/pagamentos")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class TaxasDePagamentoController {

  private final ServicoDeTaxasDePagamento taxas;

  public TaxasDePagamentoController(ServicoDeTaxasDePagamento taxas) {
    this.taxas = taxas;
  }

  @GetMapping("/contas/{id}/taxas")
  @PreAuthorize("hasRole('OWNER')")
  public List<PagamentosDtos.Taxa> listar(@PathVariable UUID id) {
    return taxas.listar(id);
  }

  @PutMapping("/contas/{id}/taxas")
  @PreAuthorize("hasRole('OWNER')")
  public List<PagamentosDtos.Taxa> salvar(@PathVariable UUID id, @RequestBody List<PagamentosDtos.Taxa> faixas) {
    return taxas.salvar(id, faixas);
  }

  @GetMapping("/recebiveis")
  @RequiresPermission("finance:view")
  public PagamentosDtos.Recebiveis recebiveis(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
    return taxas.recebiveis(de, ate);
  }
}
