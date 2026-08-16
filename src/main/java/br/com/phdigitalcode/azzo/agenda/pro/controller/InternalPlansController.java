package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CheckoutDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.CheckoutService;

/**
 * Espelha {@code modules/billing/api/internal/InternalPlansResource.java}: endpoints internos de
 * planos, exclusivos para o fluxo comercial interno (api-gerenciamento). NAO expostos ao publico.
 *
 * <p>Autenticacao: {@code /api/v1/internal/*} esta em {@code permitAll} no {@code SecurityConfig}
 * (nao ha JWT nessas chamadas, igual ao original) e e protegido pelo
 * {@link br.com.phdigitalcode.azzo.agenda.pro.security.InternalApiKeyFilter}, que exige o header
 * {@code X-Internal-Api-Key}.
 */
@RestController
@RequestMapping("/api/v1/internal/plans")
public class InternalPlansController {

  private final CheckoutService checkoutService;

  public InternalPlansController(CheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  @GetMapping("/venda-interna")
  public List<CheckoutDtos.ProductResponse> listarPlanosVendaInterna() {
    return checkoutService.listarPlanosVendaInterna();
  }

  /**
   * Todos os planos ativos (gerais + exclusivos), com {@code exclusivoVendaInterna} indicando o
   * tipo. Usado pela Nova Venda para o vendedor escolher entre planos gerais e exclusivos no mesmo
   * select.
   */
  @GetMapping("/todos")
  public List<CheckoutDtos.ProductResponse> listarTodosPlanos() {
    return checkoutService.listarTodosPlanos();
  }
}
