package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SystemAdminDtos;
import br.com.phdigitalcode.azzo.agenda.pro.service.SystemAdminService;

/** Espelha o contrato de {@code modules/admin/api/SystemAdminResource.java}. */
class SystemAdminControllerTest {

  private SystemAdminService systemAdminService;
  private SystemAdminController controller;

  @BeforeEach
  void setUp() {
    systemAdminService = mock(SystemAdminService.class);
    controller = new SystemAdminController(systemAdminService);
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(SystemAdminController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/admin/system");
  }

  @Test
  void classeExigeAdmin() {
    PreAuthorize preAuthorize = SystemAdminController.class.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).isNotNull();
    assertThat(preAuthorize.value()).contains("'ADMIN'");
  }

  @Test
  void commercialOverviewDelegaAoService() {
    SystemAdminDtos.CommercialOverviewResponse expected = new SystemAdminDtos.CommercialOverviewResponse();
    when(systemAdminService.commercialOverview()).thenReturn(expected);
    assertThat(controller.commercialOverview()).isSameAs(expected);
  }

  @Test
  void auditsDelegaAoServiceComTodosOsFiltros() {
    SystemAdminDtos.GlobalAuditListResponse expected = new SystemAdminDtos.GlobalAuditListResponse();
    when(systemAdminService.listGlobalAudits(
            "f", "t", "tid", "mod", "act", "st", "sc", "et", "au", "rid", "txt", 10))
        .thenReturn(expected);

    assertThat(controller.audits("f", "t", "tid", "mod", "act", "st", "sc", "et", "au", "rid", "txt", 10))
        .isSameAs(expected);
  }

  @Test
  void auditDetailDelegaAoService() {
    SystemAdminDtos.GlobalAuditDetailResponse expected = new SystemAdminDtos.GlobalAuditDetailResponse();
    when(systemAdminService.getGlobalAuditDetail("id-1")).thenReturn(expected);
    assertThat(controller.auditDetail("id-1")).isSameAs(expected);
  }

  @Test
  void suggestionsDelegaAoService() {
    SystemAdminDtos.GlobalSuggestionListResponse expected = new SystemAdminDtos.GlobalSuggestionListResponse();
    when(systemAdminService.listGlobalSuggestions("tid", "OPEN", "cat", "txt", 5)).thenReturn(expected);
    assertThat(controller.suggestions("tid", "OPEN", "cat", "txt", 5)).isSameAs(expected);
  }

  @Test
  void suggestionDetailDelegaAoService() {
    SystemAdminDtos.GlobalSuggestionItem expected = new SystemAdminDtos.GlobalSuggestionItem();
    when(systemAdminService.getGlobalSuggestionDetail("id-2")).thenReturn(expected);
    assertThat(controller.suggestionDetail("id-2")).isSameAs(expected);
  }

  @Test
  void updateSuggestionDelegaAoService() {
    SystemAdminDtos.SuggestionUpdateRequest request = new SystemAdminDtos.SuggestionUpdateRequest();
    SystemAdminDtos.GlobalSuggestionItem expected = new SystemAdminDtos.GlobalSuggestionItem();
    when(systemAdminService.updateGlobalSuggestion("id-3", request)).thenReturn(expected);
    assertThat(controller.updateSuggestion("id-3", request)).isSameAs(expected);
  }

  @Test
  void revokeSessionsDelegaAoService() {
    SystemAdminDtos.RevokeSessionsRequest request = new SystemAdminDtos.RevokeSessionsRequest();
    SystemAdminDtos.RevokeSessionsResponse expected = new SystemAdminDtos.RevokeSessionsResponse();
    when(systemAdminService.revokeSessions(request)).thenReturn(expected);
    assertThat(controller.revokeSessions(request)).isSameAs(expected);
  }

  @Test
  void sessionsDelegaAoService() {
    SystemAdminDtos.SessionListResponse expected = new SystemAdminDtos.SessionListResponse();
    when(systemAdminService.listSessions("tid", true, 20)).thenReturn(expected);
    assertThat(controller.sessions("tid", true, 20)).isSameAs(expected);
  }

  @Test
  void revokeSessionTokenDelegaAoService() {
    SystemAdminDtos.RevokeSessionTokenRequest request = new SystemAdminDtos.RevokeSessionTokenRequest();
    SystemAdminDtos.RevokeSessionsResponse expected = new SystemAdminDtos.RevokeSessionsResponse();
    when(systemAdminService.revokeSessionToken(request)).thenReturn(expected);
    assertThat(controller.revokeSessionToken(request)).isSameAs(expected);
  }

  @Test
  void resetUserMfaDelegaAoService() {
    UUID userId = UUID.randomUUID();
    SystemAdminDtos.MfaResetRequest request = new SystemAdminDtos.MfaResetRequest();
    SystemAdminDtos.MfaResetResponse expected = new SystemAdminDtos.MfaResetResponse();
    when(systemAdminService.resetUserMfa(userId.toString(), request)).thenReturn(expected);
    assertThat(controller.resetUserMfa(userId.toString(), request)).isSameAs(expected);
  }

  @Test
  void listPlansDelegaAoService() {
    SystemAdminDtos.PlanListResponse expected = new SystemAdminDtos.PlanListResponse();
    when(systemAdminService.listPlans()).thenReturn(expected);
    assertThat(controller.listPlans()).isSameAs(expected);
  }

  @Test
  void createPlanDelegaAoService() {
    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    SystemAdminDtos.PlanItem expected = new SystemAdminDtos.PlanItem();
    when(systemAdminService.createPlan(request)).thenReturn(expected);
    assertThat(controller.createPlan(request)).isSameAs(expected);
  }

  @Test
  void updatePlanDelegaAoService() {
    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    SystemAdminDtos.PlanItem expected = new SystemAdminDtos.PlanItem();
    when(systemAdminService.updatePlan("plan-1", request)).thenReturn(expected);
    assertThat(controller.updatePlan("plan-1", request)).isSameAs(expected);
  }

  @Test
  void updatePlanStatusDelegaAoService() {
    SystemAdminDtos.PlanStatusUpdateRequest request = new SystemAdminDtos.PlanStatusUpdateRequest();
    SystemAdminDtos.PlanItem expected = new SystemAdminDtos.PlanItem();
    when(systemAdminService.updatePlanStatus("plan-1", request)).thenReturn(expected);
    assertThat(controller.updatePlanStatus("plan-1", request)).isSameAs(expected);
  }

  @Test
  void listEmailTemplatesDelegaAoService() {
    SystemAdminDtos.EmailTemplateListResponse expected = new SystemAdminDtos.EmailTemplateListResponse();
    when(systemAdminService.listEmailTemplates()).thenReturn(expected);
    assertThat(controller.listEmailTemplates()).isSameAs(expected);
  }

  @Test
  void getEmailTemplateDelegaAoService() {
    SystemAdminDtos.EmailTemplateDetailResponse expected = new SystemAdminDtos.EmailTemplateDetailResponse();
    when(systemAdminService.getEmailTemplate("PASSWORD_RESET")).thenReturn(expected);
    assertThat(controller.getEmailTemplate("PASSWORD_RESET")).isSameAs(expected);
  }

  @Test
  void saveEmailTemplateDelegaAoService() {
    SystemAdminDtos.EmailTemplateUpsertRequest request = new SystemAdminDtos.EmailTemplateUpsertRequest();
    SystemAdminDtos.EmailTemplateDetailResponse expected = new SystemAdminDtos.EmailTemplateDetailResponse();
    when(systemAdminService.saveEmailTemplate("PASSWORD_RESET", request)).thenReturn(expected);
    assertThat(controller.saveEmailTemplate("PASSWORD_RESET", request)).isSameAs(expected);
  }

  @Test
  void previewEmailTemplateDelegaAoService() {
    SystemAdminDtos.EmailTemplateUpsertRequest request = new SystemAdminDtos.EmailTemplateUpsertRequest();
    SystemAdminDtos.EmailTemplatePreviewResponse expected = new SystemAdminDtos.EmailTemplatePreviewResponse();
    when(systemAdminService.previewEmailTemplate("PASSWORD_RESET", request)).thenReturn(expected);
    assertThat(controller.previewEmailTemplate("PASSWORD_RESET", request)).isSameAs(expected);
  }

  @Test
  void restoreDefaultEmailTemplateDelegaAoService() {
    SystemAdminDtos.EmailTemplateDetailResponse expected = new SystemAdminDtos.EmailTemplateDetailResponse();
    when(systemAdminService.restoreDefaultEmailTemplate("PASSWORD_RESET")).thenReturn(expected);
    assertThat(controller.restoreDefaultEmailTemplate("PASSWORD_RESET")).isSameAs(expected);
  }

  @Test
  void updateEmailTemplateStatusDelegaAoService() {
    SystemAdminDtos.EmailTemplateStatusUpdateRequest request = new SystemAdminDtos.EmailTemplateStatusUpdateRequest();
    SystemAdminDtos.EmailTemplateDetailResponse expected = new SystemAdminDtos.EmailTemplateDetailResponse();
    when(systemAdminService.updateEmailTemplateStatus("PASSWORD_RESET", request)).thenReturn(expected);
    assertThat(controller.updateEmailTemplateStatus("PASSWORD_RESET", request)).isSameAs(expected);
  }
}
