package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CheckoutDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.CheckoutService;
import jakarta.validation.Valid;

/** Espelha {@code modules/billing/api/CheckoutResource.java} — classe inteira sob {@code OWNER}. */
@RestController
@RequestMapping("/api/v1/checkout")
@PreAuthorize("hasRole('OWNER')")
public class CheckoutController {

  private final CheckoutService checkoutService;

  public CheckoutController(CheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  @GetMapping("/products")
  public List<CheckoutDtos.ProductResponse> listarProdutos() {
    return checkoutService.listarProdutos();
  }

  @PostMapping("/intents")
  public CheckoutDtos.CreateIntentResponse criarIntent(
      @Valid @RequestBody CheckoutDtos.CreateIntentRequest request) {
    return checkoutService.criarIntent(request);
  }

  /** O original nao recebe corpo neste POST — a intent ja carrega tudo. */
  @PostMapping("/intents/{intentId}/confirm")
  public CheckoutDtos.ConfirmIntentResponse confirmarIntent(@PathVariable UUID intentId) {
    return checkoutService.confirmarIntent(intentId);
  }
}
