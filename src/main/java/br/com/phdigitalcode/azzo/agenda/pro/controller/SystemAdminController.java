package br.com.phdigitalcode.azzo.agenda.pro.controller;

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

import br.com.phdigitalcode.azzo.agenda.pro.dto.SystemAdminDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.SystemAdminService;

/**
 * Espelha {@code modules/admin/api/SystemAdminResource.java} ({@code @Path("/api/v1/admin/system")},
 * {@code @RolesAllowed("ADMIN")} de classe).
 */
@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasRole('ADMIN')")
public class SystemAdminController {

  private final SystemAdminService systemAdminService;

  public SystemAdminController(SystemAdminService systemAdminService) {
    this.systemAdminService = systemAdminService;
  }

  @GetMapping("/commercial/overview")
  public SystemAdminDtos.CommercialOverviewResponse commercialOverview() {
    return systemAdminService.commercialOverview();
  }

  @GetMapping("/audits")
  public SystemAdminDtos.GlobalAuditListResponse audits(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "tenantId", required = false) String tenantId,
      @RequestParam(name = "module", required = false) String module,
      @RequestParam(name = "action", required = false) String action,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "sourceChannel", required = false) String sourceChannel,
      @RequestParam(name = "entityType", required = false) String entityType,
      @RequestParam(name = "actorUserId", required = false) String actorUserId,
      @RequestParam(name = "requestId", required = false) String requestId,
      @RequestParam(name = "text", required = false) String text,
      @RequestParam(name = "limit", required = false) Integer limit) {
    return systemAdminService.listGlobalAudits(
        from, to, tenantId, module, action, status, sourceChannel, entityType, actorUserId, requestId, text, limit);
  }

  @GetMapping("/audits/{id}")
  public SystemAdminDtos.GlobalAuditDetailResponse auditDetail(@PathVariable("id") String id) {
    return systemAdminService.getGlobalAuditDetail(id);
  }

  @GetMapping("/suggestions")
  public SystemAdminDtos.GlobalSuggestionListResponse suggestions(
      @RequestParam(name = "tenantId", required = false) String tenantId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "category", required = false) String category,
      @RequestParam(name = "text", required = false) String text,
      @RequestParam(name = "limit", required = false) Integer limit) {
    return systemAdminService.listGlobalSuggestions(tenantId, status, category, text, limit);
  }

  @GetMapping("/suggestions/{id}")
  public SystemAdminDtos.GlobalSuggestionItem suggestionDetail(@PathVariable("id") String id) {
    return systemAdminService.getGlobalSuggestionDetail(id);
  }

  @PatchMapping("/suggestions/{id}")
  public SystemAdminDtos.GlobalSuggestionItem updateSuggestion(
      @PathVariable("id") String id, @RequestBody(required = false) SystemAdminDtos.SuggestionUpdateRequest request) {
    return systemAdminService.updateGlobalSuggestion(id, request);
  }

  @PostMapping("/sessions/revoke")
  public SystemAdminDtos.RevokeSessionsResponse revokeSessions(
      @RequestBody(required = false) SystemAdminDtos.RevokeSessionsRequest request) {
    return systemAdminService.revokeSessions(request);
  }

  @GetMapping("/sessions")
  public SystemAdminDtos.SessionListResponse sessions(
      @RequestParam(name = "tenantId", required = false) String tenantId,
      @RequestParam(name = "includeRevoked", required = false) Boolean includeRevoked,
      @RequestParam(name = "limit", required = false) Integer limit) {
    return systemAdminService.listSessions(tenantId, includeRevoked, limit);
  }

  @PostMapping("/sessions/revoke-token")
  public SystemAdminDtos.RevokeSessionsResponse revokeSessionToken(
      @RequestBody(required = false) SystemAdminDtos.RevokeSessionTokenRequest request) {
    return systemAdminService.revokeSessionToken(request);
  }

  @PostMapping("/users/{userId}/mfa/reset")
  public SystemAdminDtos.MfaResetResponse resetUserMfa(
      @PathVariable("userId") String userId, @RequestBody(required = false) SystemAdminDtos.MfaResetRequest request) {
    return systemAdminService.resetUserMfa(userId, request);
  }

  @GetMapping("/plans")
  public SystemAdminDtos.PlanListResponse listPlans() {
    return systemAdminService.listPlans();
  }

  @PostMapping("/plans")
  public SystemAdminDtos.PlanItem createPlan(@RequestBody(required = false) SystemAdminDtos.PlanUpsertRequest request) {
    return systemAdminService.createPlan(request);
  }

  @PutMapping("/plans/{planId}")
  public SystemAdminDtos.PlanItem updatePlan(
      @PathVariable("planId") String planId, @RequestBody(required = false) SystemAdminDtos.PlanUpsertRequest request) {
    return systemAdminService.updatePlan(planId, request);
  }

  @PatchMapping("/plans/{planId}/active")
  public SystemAdminDtos.PlanItem updatePlanStatus(
      @PathVariable("planId") String planId, @RequestBody(required = false) SystemAdminDtos.PlanStatusUpdateRequest request) {
    return systemAdminService.updatePlanStatus(planId, request);
  }

  @GetMapping("/email-templates")
  public SystemAdminDtos.EmailTemplateListResponse listEmailTemplates() {
    return systemAdminService.listEmailTemplates();
  }

  @GetMapping("/email-templates/{type}")
  public SystemAdminDtos.EmailTemplateDetailResponse getEmailTemplate(@PathVariable("type") String type) {
    return systemAdminService.getEmailTemplate(type);
  }

  @PutMapping("/email-templates/{type}")
  public SystemAdminDtos.EmailTemplateDetailResponse saveEmailTemplate(
      @PathVariable("type") String type, @RequestBody(required = false) SystemAdminDtos.EmailTemplateUpsertRequest request) {
    return systemAdminService.saveEmailTemplate(type, request);
  }

  @PostMapping("/email-templates/{type}/preview")
  public SystemAdminDtos.EmailTemplatePreviewResponse previewEmailTemplate(
      @PathVariable("type") String type, @RequestBody(required = false) SystemAdminDtos.EmailTemplateUpsertRequest request) {
    return systemAdminService.previewEmailTemplate(type, request);
  }

  @PostMapping("/email-templates/{type}/restore-default")
  public SystemAdminDtos.EmailTemplateDetailResponse restoreDefaultEmailTemplate(@PathVariable("type") String type) {
    return systemAdminService.restoreDefaultEmailTemplate(type);
  }

  @PatchMapping("/email-templates/{type}/active")
  public SystemAdminDtos.EmailTemplateDetailResponse updateEmailTemplateStatus(
      @PathVariable("type") String type, @RequestBody(required = false) SystemAdminDtos.EmailTemplateStatusUpdateRequest request) {
    return systemAdminService.updateEmailTemplateStatus(type, request);
  }
}
