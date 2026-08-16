package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.AcceptTermsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.OnboardingStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoOnboarding;

/** Cobre {@code modules/onboarding/api/OnboardingResource.java} (JAX-RS -> Spring MVC). */
class OnboardingControllerTest {

  private ServicoOnboarding servicoOnboarding;
  private ContextoTenant contextoTenant;
  private AuthenticatedUser authenticatedUser;
  private OnboardingController controller;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    servicoOnboarding = mock(ServicoOnboarding.class);
    contextoTenant = mock(ContextoTenant.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    controller = new OnboardingController(servicoOnboarding, contextoTenant, authenticatedUser);
    tenantId = UUID.randomUUID();
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  @Test
  void obterStatusDelegaAoServiceComTenantDoContexto() {
    OnboardingStatusResponse esperado =
        new OnboardingStatusResponse(false, false, 0, false, false, false, false, false, null, null);
    when(servicoOnboarding.getStatus(tenantId)).thenReturn(esperado);

    assertThat(controller.obterStatus()).isSameAs(esperado);
  }

  @Test
  void aceitarTermosResolveIpViaXForwardedForEUserAgent() {
    UUID userId = UUID.randomUUID();
    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
    servletRequest.addHeader("User-Agent", "AzzoTestAgent/1.0");

    controller.aceitarTermos(request, servletRequest);

    verify(servicoOnboarding).acceptTerms(tenantId, userId, request, "203.0.113.5", "AzzoTestAgent/1.0");
  }

  @Test
  void aceitarTermosUsaXRealIpQuandoForwardedForAusente() {
    when(authenticatedUser.idOuNulo()).thenReturn(null);
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.addHeader("X-Real-IP", "198.51.100.7");

    controller.aceitarTermos(request, servletRequest);

    verify(servicoOnboarding).acceptTerms(eq(tenantId), eq(null), eq(request), eq("198.51.100.7"), eq(null));
  }

  @Test
  void aceitarTermosUsaCabecalhoForwardedQuandoOutrosAusentes() {
    when(authenticatedUser.idOuNulo()).thenReturn(null);
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.addHeader("Forwarded", "for=192.0.2.60;proto=https");

    controller.aceitarTermos(request, servletRequest);

    verify(servicoOnboarding).acceptTerms(eq(tenantId), eq(null), eq(request), eq("192.0.2.60"), eq(null));
  }

  @Test
  void aceitarTermosCaiParaIpRemotoQuandoNenhumHeaderPresente() {
    when(authenticatedUser.idOuNulo()).thenReturn(null);
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.setRemoteAddr("127.0.0.1");

    controller.aceitarTermos(request, servletRequest);

    verify(servicoOnboarding).acceptTerms(eq(tenantId), eq(null), eq(request), eq("127.0.0.1"), eq(null));
  }

  @Test
  void atualizarStepDelegaAoService() {
    controller.atualizarStep(2);

    verify(servicoOnboarding).updateStep(tenantId, 2);
  }

  @Test
  void concluirOnboardingDelegaAoService() {
    controller.concluirOnboarding();

    verify(servicoOnboarding).completeOnboarding(tenantId);
  }

  @Test
  void ignorarOnboardingDelegaAoService() {
    controller.ignorarOnboarding();

    verify(servicoOnboarding).skipOnboarding(tenantId);
  }
}
