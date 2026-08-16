package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AuditDtos;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.AuditQueryService;

/**
 * Espelha o contrato de {@code modules/audit/api/AuditoriaResource.java}: mesmo prefixo, mesma
 * faixa de permissao de classe ({@code OWNER}+{@code FINANCE}) e o efeito colateral de auditoria
 * de leitura (fire-and-forget, nunca bloqueia a resposta).
 */
class AuditoriaControllerTest {

  private final UUID tenantId = UUID.randomUUID();

  private ContextoTenant contextoTenant;
  private AuditQueryService auditQueryService;
  private AuditService auditService;
  private AuditoriaController controller;

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    auditQueryService = mock(AuditQueryService.class);
    auditService = mock(AuditService.class);
    controller = new AuditoriaController(contextoTenant, auditQueryService, auditService);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(AuditoriaController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/auditoria");
  }

  @Test
  void classeExigeOwnerOuFinance() {
    PreAuthorize preAuthorize = AuditoriaController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains("'OWNER'").contains("'FINANCE'");
  }

  @Test
  void searchDelegaAoServiceEAuditaALeitura() {
    AuditDtos.AuditSearchResponse expected = new AuditDtos.AuditSearchResponse();
    expected.limit = 50;
    expected.hasNext = false;
    when(auditQueryService.search(
            eq(tenantId), // tenantId
            any(),        // 1 from
            any(),        // 2 to
            any(),        // 3 modules
            any(),        // 4 actions
            any(),        // 5 statuses
            any(),        // 6 entityTypes
            any(),        // 7 entityId
            any(),        // 8 actorUserIds
            any(),        // 9 requestId
            any(),        // 10 sourceChannels
            any(),        // 11 ip
            any(),        // 12 hasChanges
            any(),        // 13 text
            any(),        // 14 cursor
            any()))       // 15 limit
        .thenReturn(expected);

    AuditDtos.AuditSearchResponse response = controller.search(
        null, null, "FINANCE,APPOINTMENT", null, null, null, null, null, null, null, null, null, null, null, null);

    assertThat(response).isSameAs(expected);
    verify(auditQueryService).search(
        eq(tenantId), // tenantId
        any(),        // 1 from
        any(),        // 2 to
        eq(List.of("FINANCE", "APPOINTMENT")), // 3 modules
        any(),        // 4 actions
        any(),        // 5 statuses
        any(),        // 6 entityTypes
        any(),        // 7 entityId
        any(),        // 8 actorUserIds
        any(),        // 9 requestId
        any(),        // 10 sourceChannels
        any(),        // 11 ip
        any(),        // 12 hasChanges
        any(),        // 13 text
        any(),        // 14 cursor
        any());       // 15 limit
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void searchNaoQuebraQuandoAuditoriaFalha() {
    AuditDtos.AuditSearchResponse expected = new AuditDtos.AuditSearchResponse();
    when(auditQueryService.search(
            eq(tenantId), // tenantId
            any(),        // 1 from
            any(),        // 2 to
            any(),        // 3 modules
            any(),        // 4 actions
            any(),        // 5 statuses
            any(),        // 6 entityTypes
            any(),        // 7 entityId
            any(),        // 8 actorUserIds
            any(),        // 9 requestId
            any(),        // 10 sourceChannels
            any(),        // 11 ip
            any(),        // 12 hasChanges
            any(),        // 13 text
            any(),        // 14 cursor
            any()))       // 15 limit
        .thenReturn(expected);
    when(auditService.recordSuccess(any())).thenThrow(new RuntimeException("falha auditoria"));

    AuditDtos.AuditSearchResponse response = controller.search(
        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    assertThat(response).isSameAs(expected);
  }

  @Test
  void detailDelegaAoService() {
    String id = UUID.randomUUID().toString();
    AuditDtos.AuditEventDetail expected = new AuditDtos.AuditEventDetail();
    when(auditQueryService.detail(tenantId, id)).thenReturn(expected);

    assertThat(controller.detail(id)).isSameAs(expected);
    verify(auditService).recordSuccess(any());
  }

  @Test
  void filterOptionsDelegaAoService() {
    AuditDtos.AuditFilterOptionsResponse expected = new AuditDtos.AuditFilterOptionsResponse();
    when(auditQueryService.filterOptions(tenantId, "2026-01-01", "2026-01-31")).thenReturn(expected);

    assertThat(controller.filterOptions("2026-01-01", "2026-01-31")).isSameAs(expected);
  }

  @Test
  void retentionEventsDelegaAoService() {
    AuditDtos.AuditRetentionPageResponse expected = new AuditDtos.AuditRetentionPageResponse();
    expected.items = List.of();
    when(auditQueryService.listRetentionEventsPaged(tenantId, null, null, null, 10)).thenReturn(expected);

    assertThat(controller.retentionEvents(null, null, null, 10)).isSameAs(expected);
  }

  @Test
  void exportDelegaAoService() {
    AuditDtos.AuditExportRequest request = new AuditDtos.AuditExportRequest();
    AuditDtos.AuditExportResponse expected = new AuditDtos.AuditExportResponse();
    expected.exportId = "export-1";
    expected.format = "JSON";
    expected.expiresAt = "2026-01-01T00:00:00Z";
    when(auditQueryService.export(tenantId, request)).thenReturn(expected);

    assertThat(controller.export(request)).isSameAs(expected);
    verify(auditService).recordSuccess(any());
  }

  @Test
  void downloadExportDevolveConteudoComContentDisposition() {
    AuditQueryService.ExportDownload download =
        new AuditQueryService.ExportDownload("{}", "JSON", "application/json; charset=UTF-8");
    when(auditQueryService.downloadExport("export-1")).thenReturn(download);

    ResponseEntity<String> response = controller.downloadExport("export-1");

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).isEqualTo("{}");
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .isEqualTo("attachment; filename=\"auditoria-export-1.json\"");
  }

  @Test
  void downloadExportUsaExtensaoCsvQuandoFormatoCsv() {
    AuditQueryService.ExportDownload download =
        new AuditQueryService.ExportDownload("a,b\n1,2", "CSV", "text/csv; charset=UTF-8");
    when(auditQueryService.downloadExport("export-2")).thenReturn(download);

    ResponseEntity<String> response = controller.downloadExport("export-2");

    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .isEqualTo("attachment; filename=\"auditoria-export-2.csv\"");
  }
}
