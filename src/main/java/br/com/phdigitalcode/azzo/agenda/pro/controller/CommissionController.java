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

/** Espelha {@code modules/commission/api/CommissionResource.java} ({@code @RolesAllowed("OWNER")}). */
@RestController
@RequestMapping("/api/v1/commissions")
@PreAuthorize("hasRole('OWNER')")
public class CommissionController {

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
  public CommissionDtos.RuleSetResponse createRuleSet(
      @Valid @RequestBody CommissionDtos.RuleSetUpsertRequest request) {
    return commissionService.createRuleSet(request);
  }

  @PutMapping("/rules/{ruleSetId}")
  public CommissionDtos.RuleSetResponse updateRuleSet(
      @PathVariable UUID ruleSetId, @Valid @RequestBody CommissionDtos.RuleSetUpsertRequest request) {
    return commissionService.updateRuleSet(ruleSetId, request);
  }

  @PatchMapping("/rules/{ruleSetId}/active")
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
  public CommissionDtos.CycleResponse closeCycle(
      @Valid @RequestBody CommissionDtos.CycleCloseRequest request) {
    return commissionService.closeCycle(request);
  }

  @PostMapping("/cycles/{cycleId}/pay")
  public CommissionDtos.CycleResponse payCycle(
      @PathVariable UUID cycleId, @Valid @RequestBody CommissionDtos.CyclePayRequest request) {
    return commissionService.payCycle(cycleId, request);
  }

  @PostMapping("/adjustments")
  public CommissionDtos.AdjustmentResponse createAdjustment(
      @Valid @RequestBody CommissionDtos.AdjustmentRequest request) {
    return commissionService.createAdjustment(request);
  }
}
