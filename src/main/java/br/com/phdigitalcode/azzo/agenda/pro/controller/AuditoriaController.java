package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AuditDtos;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuditQueryService;

/**
 * Espelha {@code modules/audit/api/AuditoriaResource.java} ({@code @Path("/api/v1/auditoria")},
 * {@code @RolesAllowed({"OWNER", "FINANCE"})} de classe — nao ha faixas diferentes por metodo aqui,
 * ao contrario de {@code BillingController}).
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@PreAuthorize("hasAnyRole('OWNER', 'FINANCE')")
public class AuditoriaController {

  private final ContextoTenant contextoTenant;
  private final AuditQueryService auditQueryService;
  private final AuditService auditService;

  public AuditoriaController(
      ContextoTenant contextoTenant, AuditQueryService auditQueryService, AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.auditQueryService = auditQueryService;
    this.auditService = auditService;
  }

  @GetMapping("/events")
  public AuditDtos.AuditSearchResponse search(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "modules", required = false) String modules,
      @RequestParam(name = "actions", required = false) String actions,
      @RequestParam(name = "statuses", required = false) String statuses,
      @RequestParam(name = "entityTypes", required = false) String entityTypes,
      @RequestParam(name = "entityId", required = false) String entityId,
      @RequestParam(name = "actorUserIds", required = false) String actorUserIds,
      @RequestParam(name = "requestId", required = false) String requestId,
      @RequestParam(name = "sourceChannels", required = false) String sourceChannels,
      @RequestParam(name = "ip", required = false) String ip,
      @RequestParam(name = "hasChanges", required = false) Boolean hasChanges,
      @RequestParam(name = "text", required = false) String text,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "limit", required = false) Integer limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AuditDtos.AuditSearchResponse response = auditQueryService.search(
        tenantId,
        from,
        to,
        splitList(modules),
        splitList(actions),
        splitList(statuses),
        splitList(entityTypes),
        entityId,
        splitList(actorUserIds),
        requestId,
        splitList(sourceChannels),
        ip,
        hasChanges,
        text,
        cursor,
        limit);
    auditarLeitura(tenantId, "AUDIT_EVENTS_SEARCH", null, Map.of("limit", response.limit, "hasNext", response.hasNext));
    return response;
  }

  @GetMapping("/events/{id}")
  public AuditDtos.AuditEventDetail detail(@PathVariable("id") String id) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AuditDtos.AuditEventDetail detail = auditQueryService.detail(tenantId, id);
    auditarLeitura(tenantId, "AUDIT_EVENT_DETAIL", id, null);
    return detail;
  }

  @GetMapping("/filters/options")
  public AuditDtos.AuditFilterOptionsResponse filterOptions(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AuditDtos.AuditFilterOptionsResponse response = auditQueryService.filterOptions(tenantId, from, to);
    auditarLeitura(tenantId, "AUDIT_FILTER_OPTIONS", null, null);
    return response;
  }

  @GetMapping("/retention/events")
  public AuditDtos.AuditRetentionPageResponse retentionEvents(
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "to", required = false) String to,
      @RequestParam(name = "executionId", required = false) String executionId,
      @RequestParam(name = "limit", required = false) Integer limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AuditDtos.AuditRetentionPageResponse response =
        auditQueryService.listRetentionEventsPaged(tenantId, from, to, executionId, limit);
    auditarLeitura(tenantId, "AUDIT_RETENTION_EVENTS_READ", null, Map.of("count", response.items.size()));
    return response;
  }

  @PostMapping("/events/export")
  public AuditDtos.AuditExportResponse export(@RequestBody(required = false) AuditDtos.AuditExportRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AuditDtos.AuditExportResponse response = auditQueryService.export(tenantId, request);
    auditarLeitura(
        tenantId,
        "AUDIT_EVENTS_EXPORT",
        response.exportId,
        Map.of("format", response.format, "expiresAt", response.expiresAt));
    return response;
  }

  @GetMapping("/events/export/{exportId}")
  public ResponseEntity<String> downloadExport(@PathVariable("exportId") String exportId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    AuditQueryService.ExportDownload download = auditQueryService.downloadExport(exportId);
    String filename = "auditoria-" + exportId + ("CSV".equals(download.format()) ? ".csv" : ".json");
    auditarLeitura(tenantId, "AUDIT_EVENTS_EXPORT_DOWNLOAD", exportId, null);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(download.contentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(download.payload());
  }

  private void auditarLeitura(UUID tenantId, String action, String entityId, Object metadata) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.module = AuditConstants.Module.SYSTEM;
      command.action = action;
      command.entityType = "AUDIT_READ";
      command.entityId = entityId;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.metadata = metadata;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // nao bloquear endpoint por falha de auditoria.
    }
  }

  private List<String> splitList(String value) {
    if (value == null || value.isBlank()) return List.of();
    return Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList();
  }
}
