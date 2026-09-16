package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ComandaDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoComanda;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/pos/api/ComandaResource.java} — mesmos paths, verbos, parametros e roles.
 *
 * <p>{@code abrir} recebe o corpo como {@code required = false} porque o original nao anota o
 * parametro com {@code @Valid} nem exige corpo ({@code ServicoComanda.abrir} trata {@code null}
 * explicitamente).
 *
 * <p>⚠️ <b>Cada rota exige permissao</b>, e nao so o papel: {@code pos:view} para ler e {@code
 * pos:manage} para mexer no dinheiro. Antes havia apenas o {@code hasAnyRole} da classe — quem
 * tivesse o papel operava o PDV mesmo com um perfil de acesso SEM {@code /pos}: o menu escondia a
 * tela e a API aceitava a chamada (achado do teste de ponta a ponta de 2026-09-16). O fechamento de
 * caixa, ao lado, sempre exigiu {@code finance:view}/{@code finance:manage}. Codigos criados na
 * V130, concedidos a OWNER e PROFESSIONAL pelo papel; para STAFF vem do perfil de acesso.
 */
@RestController
@RequestMapping("/api/v1/pos/comandas")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class ComandaController {

  private final ServicoComanda servicoComanda;

  public ComandaController(ServicoComanda servicoComanda) {
    this.servicoComanda = servicoComanda;
  }

  @GetMapping
  @RequiresPermission("pos:view")
  public ComandaDtos.ComandaPageResponse listar(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return servicoComanda.listar(status, page, size);
  }

  @PostMapping
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse abrir(
      @RequestBody(required = false) ComandaDtos.AbrirComandaRequest request) {
    return servicoComanda.abrir(request);
  }

  @GetMapping("/{id}")
  @RequiresPermission("pos:view")
  public ComandaDtos.ComandaResponse obter(@PathVariable UUID id) {
    return servicoComanda.obter(id);
  }

  @PostMapping("/{id}/itens")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse adicionarItem(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.AdicionarItemRequest request) {
    return servicoComanda.adicionarItem(id, request);
  }

  @DeleteMapping("/{id}/itens/{itemId}")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse removerItem(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return servicoComanda.removerItem(id, itemId);
  }

  @PostMapping("/{id}/desconto")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse aplicarDesconto(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.AplicarDescontoRequest request) {
    return servicoComanda.aplicarDesconto(id, request);
  }

  @PostMapping("/{id}/gorjeta")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse registrarGorjeta(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.RegistrarGorjetaRequest request) {
    return servicoComanda.registrarGorjeta(id, request);
  }

  @PostMapping("/{id}/pagamentos")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse registrarPagamento(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.RegistrarPagamentoRequest request) {
    return servicoComanda.registrarPagamento(id, request);
  }

  @PostMapping("/{id}/fidelidade/resgatar")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse resgatarFidelidade(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.ResgatarFidelidadeRequest request) {
    return servicoComanda.resgatarFidelidade(id, request);
  }

  @PostMapping("/{id}/fechar")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse fechar(@PathVariable UUID id) {
    return servicoComanda.fechar(id);
  }

  @PostMapping("/{id}/cancelar")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse cancelar(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.CancelarComandaRequest request) {
    return servicoComanda.cancelar(id, request);
  }

  @PostMapping("/{id}/estornar")
  @PreAuthorize("hasRole('OWNER')")
  @RequiresPermission("pos:manage")
  public ComandaDtos.ComandaResponse estornar(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.EstornarComandaRequest request) {
    return servicoComanda.estornar(id, request);
  }
}
