package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CupomDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeCupons;
import jakarta.validation.Valid;

/**
 * Cadastro de cupons e o gerador de lotes (item 6). Quem gerencia: o dono ou um perfil com o item
 * "Cupons e vales-presente" ({@code pos:coupons}, V181). Aplicar cupom na comanda e do PDV ({@code pos:manage}).
 */
@RestController
@RequestMapping("/api/v1/cupons")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
@RequiresPermission("pos:coupons")
public class CuponsController {

  private final ServicoDeCupons servicoDeCupons;

  public CuponsController(ServicoDeCupons servicoDeCupons) {
    this.servicoDeCupons = servicoDeCupons;
  }

  @GetMapping
  public List<CupomDtos.CupomResponse> listar() {
    return servicoDeCupons.listar();
  }

  @PostMapping
  public CupomDtos.CupomResponse criar(@Valid @RequestBody CupomDtos.CupomRequest request) {
    return servicoDeCupons.criar(request);
  }

  @PutMapping("/{id}")
  public CupomDtos.CupomResponse editar(@PathVariable UUID id, @Valid @RequestBody CupomDtos.CupomRequest request) {
    return servicoDeCupons.editar(id, request);
  }

  /** Se a comanda aceita mais de um cupom (V183). */
  @GetMapping("/configuracao")
  public CupomDtos.ConfiguracaoDeCupons configuracao() {
    return servicoDeCupons.configuracao();
  }

  @PutMapping("/configuracao")
  public CupomDtos.ConfiguracaoDeCupons salvarConfiguracao(@RequestBody CupomDtos.ConfiguracaoDeCupons request) {
    return servicoDeCupons.salvarConfiguracao(request);
  }

  @GetMapping("/{id}/usos")
  public List<CupomDtos.UsoResponse> usos(@PathVariable UUID id) {
    return servicoDeCupons.usos(id);
  }

  // ─── Gerador ───────────────────────────────────────────────────────────────

  @GetMapping("/lotes")
  public List<CupomDtos.LoteResumoResponse> listarLotes() {
    return servicoDeCupons.listarLotes();
  }

  @PostMapping("/lotes")
  public CupomDtos.LoteResponse gerarLote(@Valid @RequestBody CupomDtos.LoteRequest request) {
    return servicoDeCupons.gerarLote(request);
  }

  @GetMapping("/lotes/{id}")
  public CupomDtos.LoteResponse acompanhar(
      @PathVariable UUID id, @RequestParam(name = "situacao", required = false) String situacao) {
    return servicoDeCupons.acompanhar(id, situacao);
  }

  @PostMapping("/lotes/{id}/mais")
  public CupomDtos.LoteResponse gerarMais(
      @PathVariable UUID id, @Valid @RequestBody CupomDtos.MaisCodigosRequest request) {
    return servicoDeCupons.gerarMais(id, request.quantidade);
  }

  @PostMapping("/lotes/{id}/desativar")
  public CupomDtos.LoteResponse desativar(@PathVariable UUID id) {
    return servicoDeCupons.desativarLote(id);
  }

  @GetMapping("/lotes/{id}/exportar")
  public ResponseEntity<byte[]> exportar(@PathVariable UUID id) {
    // BOM: o Excel em portugues abre os acentos certos.
    byte[] corpo = ("﻿" + servicoDeCupons.exportarLote(id)).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + servicoDeCupons.nomeDoArquivoDoLote(id) + "\"")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
        .body(corpo);
  }
}
