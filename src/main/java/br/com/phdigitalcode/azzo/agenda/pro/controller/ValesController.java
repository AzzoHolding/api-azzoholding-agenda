package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.ValeDtos;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeVales;
import jakarta.validation.Valid;

/**
 * Vales-presente (item 6). A lista e a gestao sao de quem tem {@code pos:coupons}; ajustar, cancelar e prorrogar
 * sao so do dono (conferido no servico). Consultar o saldo pelo codigo e do caixa ({@code pos:view}).
 */
@RestController
@RequestMapping("/api/v1/vales-presente")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL', 'STAFF')")
public class ValesController {

  private final ServicoDeVales servicoDeVales;

  public ValesController(ServicoDeVales servicoDeVales) {
    this.servicoDeVales = servicoDeVales;
  }

  @GetMapping
  @RequiresPermission("pos:coupons")
  public ValeDtos.ListaDeValesResponse listar(@RequestParam(name = "busca", required = false) String busca) {
    return servicoDeVales.listar(busca);
  }

  @GetMapping("/{id}")
  @RequiresPermission("pos:coupons")
  public ValeDtos.ValeResponse obter(@PathVariable UUID id) {
    return servicoDeVales.obter(id);
  }

  /** O caixa confere o vale antes de receber com ele: saldo, validade e situacao. */
  @GetMapping("/codigo/{codigo}")
  @RequiresPermission("pos:view")
  public ValeDtos.ValeResponse consultar(@PathVariable String codigo) {
    return servicoDeVales.consultar(codigo);
  }

  @PostMapping("/{id}/ajustar")
  @RequiresPermission("pos:coupons")
  public ValeDtos.ValeResponse ajustar(@PathVariable UUID id, @Valid @RequestBody ValeDtos.AjustarRequest request) {
    return servicoDeVales.ajustar(id, request);
  }

  @PostMapping("/{id}/cancelar")
  @RequiresPermission("pos:coupons")
  public ValeDtos.ValeResponse cancelar(@PathVariable UUID id, @Valid @RequestBody ValeDtos.CancelarRequest request) {
    return servicoDeVales.cancelar(id, request);
  }

  @PostMapping("/{id}/prorrogar")
  @RequiresPermission("pos:coupons")
  public ValeDtos.ValeResponse prorrogar(@PathVariable UUID id, @Valid @RequestBody ValeDtos.ProrrogarRequest request) {
    return servicoDeVales.prorrogar(id, request);
  }
}
