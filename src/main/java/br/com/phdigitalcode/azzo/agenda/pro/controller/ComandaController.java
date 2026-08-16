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
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoComanda;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/pos/api/ComandaResource.java} — mesmos paths, verbos, parametros e roles.
 *
 * <p>{@code abrir} recebe o corpo como {@code required = false} porque o original nao anota o
 * parametro com {@code @Valid} nem exige corpo ({@code ServicoComanda.abrir} trata {@code null}
 * explicitamente).
 */
@RestController
@RequestMapping("/api/v1/pos/comandas")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class ComandaController {

  private final ServicoComanda servicoComanda;

  public ComandaController(ServicoComanda servicoComanda) {
    this.servicoComanda = servicoComanda;
  }

  @GetMapping
  public ComandaDtos.ComandaPageResponse listar(
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return servicoComanda.listar(status, page, size);
  }

  @PostMapping
  public ComandaDtos.ComandaResponse abrir(
      @RequestBody(required = false) ComandaDtos.AbrirComandaRequest request) {
    return servicoComanda.abrir(request);
  }

  @GetMapping("/{id}")
  public ComandaDtos.ComandaResponse obter(@PathVariable UUID id) {
    return servicoComanda.obter(id);
  }

  @PostMapping("/{id}/itens")
  public ComandaDtos.ComandaResponse adicionarItem(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.AdicionarItemRequest request) {
    return servicoComanda.adicionarItem(id, request);
  }

  @DeleteMapping("/{id}/itens/{itemId}")
  public ComandaDtos.ComandaResponse removerItem(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return servicoComanda.removerItem(id, itemId);
  }

  @PostMapping("/{id}/desconto")
  public ComandaDtos.ComandaResponse aplicarDesconto(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.AplicarDescontoRequest request) {
    return servicoComanda.aplicarDesconto(id, request);
  }

  @PostMapping("/{id}/gorjeta")
  public ComandaDtos.ComandaResponse registrarGorjeta(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.RegistrarGorjetaRequest request) {
    return servicoComanda.registrarGorjeta(id, request);
  }

  @PostMapping("/{id}/pagamentos")
  public ComandaDtos.ComandaResponse registrarPagamento(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.RegistrarPagamentoRequest request) {
    return servicoComanda.registrarPagamento(id, request);
  }

  @PostMapping("/{id}/fidelidade/resgatar")
  public ComandaDtos.ComandaResponse resgatarFidelidade(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.ResgatarFidelidadeRequest request) {
    return servicoComanda.resgatarFidelidade(id, request);
  }

  @PostMapping("/{id}/fechar")
  public ComandaDtos.ComandaResponse fechar(@PathVariable UUID id) {
    return servicoComanda.fechar(id);
  }

  @PostMapping("/{id}/cancelar")
  @PreAuthorize("hasRole('OWNER')")
  public ComandaDtos.ComandaResponse cancelar(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.CancelarComandaRequest request) {
    return servicoComanda.cancelar(id, request);
  }

  @PostMapping("/{id}/estornar")
  @PreAuthorize("hasRole('OWNER')")
  public ComandaDtos.ComandaResponse estornar(
      @PathVariable UUID id, @Valid @RequestBody ComandaDtos.EstornarComandaRequest request) {
    return servicoComanda.estornar(id, request);
  }
}
