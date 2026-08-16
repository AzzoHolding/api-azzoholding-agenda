package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.OptOutPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationCyclesPagedResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.ReactivationMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.TenantReactivationConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ReactivationDtos.UpdateReactivationConfigRequest;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ReactivationConfigService;

/**
 * Espelha o contrato de {@code modules/chat/api/ReactivationConfigResource.java}: mesmo prefixo,
 * mesma faixa de permissao por rota (leitura {@code OWNER}+{@code ADMIN}, escrita {@code OWNER}).
 */
class ReactivationConfigControllerTest {

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  private ReactivationConfigService reactivationConfigService;
  private ContextoTenant contextoTenant;
  private AuthenticatedUser authenticatedUser;
  private ReactivationConfigController controller;

  @BeforeEach
  void setUp() {
    reactivationConfigService = mock(ReactivationConfigService.class);
    contextoTenant = mock(ContextoTenant.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    controller = new ReactivationConfigController(reactivationConfigService, contextoTenant, authenticatedUser);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(authenticatedUser.roleOuNulo()).thenReturn("OWNER");
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(ReactivationConfigController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/reactivation");
  }

  @Test
  void getConfigDelegaAoServiceComTenantDoContexto() {
    TenantReactivationConfigResponse expected = new TenantReactivationConfigResponse();
    when(reactivationConfigService.getConfig(tenantId)).thenReturn(expected);

    TenantReactivationConfigResponse response = controller.getConfig();

    assertThat(response).isSameAs(expected);
    assertMethodHasRoles("getConfig", "OWNER", "ADMIN");
  }

  @Test
  void updateConfigDelegaComUsuarioERole() {
    UpdateReactivationConfigRequest request = new UpdateReactivationConfigRequest();
    TenantReactivationConfigResponse expected = new TenantReactivationConfigResponse();
    when(reactivationConfigService.updateConfig(tenantId, userId, "OWNER", request)).thenReturn(expected);

    TenantReactivationConfigResponse response = controller.updateConfig(request);

    assertThat(response).isSameAs(expected);
    assertMethodHasRoles("updateConfig", "OWNER");
  }

  @Test
  void optOutDelegaAoServiceERetornaOk() {
    UUID clientId = UUID.randomUUID();

    var response = controller.optOut(clientId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    verify(reactivationConfigService).registrarOptOut(tenantId, clientId, userId, "OWNER");
    assertMethodHasRoles("optOut", "OWNER");
  }

  @Test
  void optInDelegaAoServiceERetornaOk() {
    UUID clientId = UUID.randomUUID();

    var response = controller.optIn(clientId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    verify(reactivationConfigService).registrarOptIn(tenantId, clientId, userId, "OWNER");
    assertMethodHasRoles("optIn", "OWNER");
  }

  @Test
  void listCyclesRepassaFiltrosEPaginacao() {
    ReactivationCyclesPagedResponse expected = new ReactivationCyclesPagedResponse();
    when(reactivationConfigService.listCycles(tenantId, "ACTIVE", "2026-06-01", "2026-06-30", 1, 10))
        .thenReturn(expected);

    ReactivationCyclesPagedResponse response =
        controller.listCycles("ACTIVE", "2026-06-01", "2026-06-30", 1, 10);

    assertThat(response).isSameAs(expected);
    assertMethodHasRoles("listCycles", "OWNER", "ADMIN");
  }

  @Test
  void cancelCycleDelegaAoService() {
    UUID cycleId = UUID.randomUUID();

    var response = controller.cancelCycle(cycleId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    verify(reactivationConfigService).cancelCycleById(tenantId, cycleId);
    assertMethodHasRoles("cancelCycle", "OWNER");
  }

  @Test
  void listOptOutsRepassaPaginacao() {
    OptOutPagedResponse expected = new OptOutPagedResponse();
    when(reactivationConfigService.listOptOutsPaged(tenantId, 2, 50)).thenReturn(expected);

    OptOutPagedResponse response = controller.listOptOuts(2, 50);

    assertThat(response).isSameAs(expected);
    assertMethodHasRoles("listOptOuts", "OWNER", "ADMIN");
  }

  @Test
  void getMetricsRepassaPeriodo() {
    ReactivationMetricsResponse expected = new ReactivationMetricsResponse();
    when(reactivationConfigService.getMetrics(tenantId, "2026-06")).thenReturn(expected);

    ReactivationMetricsResponse response = controller.getMetrics("2026-06");

    assertThat(response).isSameAs(expected);
    assertMethodHasRoles("getMetrics", "OWNER", "ADMIN");
  }

  private void assertMethodHasRoles(String methodName, String... expectedRoles) {
    Method match = null;
    for (Method m : ReactivationConfigController.class.getDeclaredMethods()) {
      if (m.getName().equals(methodName)) {
        match = m;
        break;
      }
    }
    assertThat(match).as("metodo %s deve existir", methodName).isNotNull();
    PreAuthorize preAuthorize = match.getAnnotation(PreAuthorize.class);
    assertThat(preAuthorize).as("metodo %s deve ter @PreAuthorize", methodName).isNotNull();
    for (String role : expectedRoles) {
      assertThat(preAuthorize.value()).contains("'" + role + "'");
    }
  }
}
