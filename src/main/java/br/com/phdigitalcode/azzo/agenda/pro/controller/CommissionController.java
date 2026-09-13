package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.CommissionDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.CommissionService;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/commission/api/CommissionResource.java} ({@code @RolesAllowed("OWNER")}).
 *
 * <p>Desde a V127 entra tambem quem recebeu a tela Comissoes num perfil de acesso: leitura com
 * {@code commission:view} (gate da classe) e escrita — regras, fechar e pagar ciclo, ajuste — com
 * {@code commission:manage} (gate do metodo, que substitui o da classe). O papel OWNER continua
 * valendo sozinho, e os codigos nao vao para o ADMIN.
 */
@RestController
@RequestMapping("/api/v1/commissions")
@PreAuthorize(CommissionController.LEITURA)
public class CommissionController {

  static final String LEITURA = "hasRole('OWNER') or @permissionService.possuiPermissao('commission:view')";
  static final String ESCRITA = "hasRole('OWNER') or @permissionService.possuiPermissao('commission:manage')";

  private final CommissionService commissionService;

  public CommissionController(CommissionService commissionService) {
    this.commissionService = commissionService;
  }

  @GetMapping("/rules")
  public CommissionDtos.RuleSetListResponse listRuleSets(
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) Boolean activeOnly) {
    return commissionService.listRuleSets(professionalId, activeOnly);
  }

  @PostMapping("/rules")
  @PreAuthorize(ESCRITA)
  public CommissionDtos.RuleSetResponse createRuleSet(
      @Valid @RequestBody CommissionDtos.RuleSetUpsertRequest request) {
    return commissionService.createRuleSet(request);
  }

  @PutMapping("/rules/{ruleSetId}")
  @PreAuthorize(ESCRITA)
  public CommissionDtos.RuleSetResponse updateRuleSet(
      @PathVariable UUID ruleSetId, @Valid @RequestBody CommissionDtos.RuleSetUpsertRequest request) {
    return commissionService.updateRuleSet(ruleSetId, request);
  }

  @PatchMapping("/rules/{ruleSetId}/active")
  @PreAuthorize(ESCRITA)
  public CommissionDtos.RuleSetResponse setRuleSetActive(
      @PathVariable UUID ruleSetId, @Valid @RequestBody CommissionDtos.ActivationRequest request) {
    return commissionService.setRuleSetActive(ruleSetId, request);
  }

  @GetMapping("/report")
  public CommissionDtos.ReportResponse report(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String professionalId,
      @RequestParam(required = false) String status) {
    return commissionService.report(from, to, professionalId, status);
  }

  @GetMapping("/report/{professionalId}")
  public CommissionDtos.ProfessionalReportResponse reportByProfessional(
      @PathVariable UUID professionalId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return commissionService.reportByProfessional(professionalId, from, to);
  }

  @GetMapping("/cycles")
  public CommissionDtos.CycleListResponse cycles(@RequestParam(required = false) String status) {
    return commissionService.listCycles(status);
  }

  @PostMapping("/cycles/close")
  @PreAuthorize(ESCRITA)
  public CommissionDtos.CycleResponse closeCycle(
      @Valid @RequestBody CommissionDtos.CycleCloseRequest request) {
    return commissionService.closeCycle(request);
  }

  @PostMapping("/cycles/{cycleId}/pay")
  @PreAuthorize(ESCRITA)
  public CommissionDtos.CycleResponse payCycle(
      @PathVariable UUID cycleId, @Valid @RequestBody CommissionDtos.CyclePayRequest request) {
    return commissionService.payCycle(cycleId, request);
  }

  @PostMapping("/adjustments")
  @PreAuthorize(ESCRITA)
  public CommissionDtos.AdjustmentResponse createAdjustment(
      @Valid @RequestBody CommissionDtos.AdjustmentRequest request) {
    return commissionService.createAdjustment(request);
  }
}
