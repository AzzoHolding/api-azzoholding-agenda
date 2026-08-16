package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.AcceptTermsRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.OnboardingDtos.OnboardingStatusResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsAcceptance;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantBusinessHoursRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TermsAcceptanceRepository;

/** Cobre {@code modules/onboarding/application/ServicoOnboarding.java}. */
class ServicoOnboardingTest {

  private TenantRepository tenantRepository;
  private TermsAcceptanceRepository termsAcceptanceRepository;
  private AuditService auditService;
  private ProfissionalRepository profissionalRepository;
  private ServicoRepository servicoRepository;
  private TenantBusinessHoursRepository tenantBusinessHoursRepository;
  private ServicoOnboarding service;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    termsAcceptanceRepository = mock(TermsAcceptanceRepository.class);
    auditService = mock(AuditService.class);
    profissionalRepository = mock(ProfissionalRepository.class);
    servicoRepository = mock(ServicoRepository.class);
    tenantBusinessHoursRepository = mock(TenantBusinessHoursRepository.class);
    service = new ServicoOnboarding(
        tenantRepository, termsAcceptanceRepository, auditService,
        profissionalRepository, servicoRepository, tenantBusinessHoursRepository);
    tenantId = UUID.randomUUID();
  }

  private Tenant tenantExistente() {
    Tenant tenant = new Tenant();
    tenant.setId(tenantId);
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    return tenant;
  }

  @Test
  void getStatusLancaNotFoundQuandoTenantNaoExiste() {
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getStatus(tenantId))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(404);
  }

  @Test
  void getStatusRetornaTodosOsIndicadoresComTermosAceitos() {
    Tenant tenant = tenantExistente();
    tenant.setOnboardingComplete(true);
    tenant.setOnboardingSkipped(false);
    tenant.setOnboardingStep(2);
    LocalDateTime completedAt = LocalDateTime.now();
    tenant.setOnboardingCompletedAt(completedAt);

    when(profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(servicoRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(profissionalRepository.countServiceAssignmentsByTenantId(tenantId)).thenReturn(1L);
    when(tenantBusinessHoursRepository.countByTenantIdAndEnabledTrue(tenantId)).thenReturn(1L);
    when(termsAcceptanceRepository.hasAccepted(tenantId)).thenReturn(true);
    TermsAcceptance latest = new TermsAcceptance();
    latest.setTermsVersion("v2");
    when(termsAcceptanceRepository.findLatestByTenantId(tenantId)).thenReturn(Optional.of(latest));

    OnboardingStatusResponse response = service.getStatus(tenantId);

    assertThat(response.onboardingComplete()).isTrue();
    assertThat(response.onboardingSkipped()).isFalse();
    assertThat(response.currentStep()).isEqualTo(2);
    assertThat(response.hasProfessionals()).isTrue();
    assertThat(response.hasServices()).isTrue();
    assertThat(response.hasAssignments()).isTrue();
    assertThat(response.hasBusinessHours()).isTrue();
    assertThat(response.termsAccepted()).isTrue();
    assertThat(response.termsVersion()).isEqualTo("v2");
    assertThat(response.completedAt()).isEqualTo(completedAt);
  }

  @Test
  void getStatusNaoConsultaVersaoDeTermosQuandoNaoAceito() {
    tenantExistente();
    when(termsAcceptanceRepository.hasAccepted(tenantId)).thenReturn(false);

    OnboardingStatusResponse response = service.getStatus(tenantId);

    assertThat(response.termsAccepted()).isFalse();
    assertThat(response.termsVersion()).isNull();
    verify(termsAcceptanceRepository, never()).findLatestByTenantId(any());
  }

  @Test
  void getStatusUsaZeroComoStepPadraoQuandoNulo() {
    Tenant tenant = tenantExistente();
    tenant.setOnboardingStep(null);

    OnboardingStatusResponse response = service.getStatus(tenantId);

    assertThat(response.currentStep()).isZero();
  }

  @Test
  void acceptTermsPersisteAceiteEAvancaStepQuandoAbaixoDeUm() {
    Tenant tenant = tenantExistente();
    tenant.setOnboardingStep(0);
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    service.acceptTerms(tenantId, UUID.randomUUID(), request, "1.2.3.4", "agent");

    verify(termsAcceptanceRepository).save(any(TermsAcceptance.class));
    assertThat(tenant.getOnboardingStep()).isEqualTo(1);
    verify(tenantRepository).save(tenant);
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void acceptTermsNaoRegrideStepQuandoJaAvancado() {
    Tenant tenant = tenantExistente();
    tenant.setOnboardingStep(3);
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    service.acceptTerms(tenantId, UUID.randomUUID(), request, null, null);

    assertThat(tenant.getOnboardingStep()).isEqualTo(3);
  }

  @Test
  void acceptTermsLancaNotFoundQuandoTenantNaoExiste() {
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
    AcceptTermsRequest request = new AcceptTermsRequest("v1", "p1");

    assertThatThrownBy(() -> service.acceptTerms(tenantId, UUID.randomUUID(), request, null, null))
        .isInstanceOf(ApiClientErrorException.class);
  }

  @Test
  void updateStepAtualizaQuandoMaiorQueAtual() {
    Tenant tenant = tenantExistente();
    tenant.setOnboardingStep(1);

    service.updateStep(tenantId, 3);

    assertThat(tenant.getOnboardingStep()).isEqualTo(3);
    verify(tenantRepository).save(tenant);
  }

  @Test
  void updateStepNaoAtualizaQuandoMenorOuIgualAoAtual() {
    Tenant tenant = tenantExistente();
    tenant.setOnboardingStep(3);

    service.updateStep(tenantId, 2);

    assertThat(tenant.getOnboardingStep()).isEqualTo(3);
    verify(tenantRepository, never()).save(any());
  }

  @Test
  void updateStepRejeitaStepNegativo() {
    tenantExistente();

    assertThatThrownBy(() -> service.updateStep(tenantId, -1))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(400);
  }

  @Test
  void completeOnboardingFalhaQuandoSemProfissionais() {
    tenantExistente();
    when(profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(0L);

    assertThatThrownBy(() -> service.completeOnboarding(tenantId))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("profissional")
        .extracting(e -> ((ApiClientErrorException) e).getStatus())
        .isEqualTo(422);
  }

  @Test
  void completeOnboardingFalhaQuandoSemServicos() {
    tenantExistente();
    when(profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(servicoRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(0L);

    assertThatThrownBy(() -> service.completeOnboarding(tenantId))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("servico");
  }

  @Test
  void completeOnboardingFalhaQuandoSemVinculos() {
    tenantExistente();
    when(profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(servicoRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(profissionalRepository.countServiceAssignmentsByTenantId(tenantId)).thenReturn(0L);

    assertThatThrownBy(() -> service.completeOnboarding(tenantId))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessageContaining("Vincule");
  }

  @Test
  void completeOnboardingMarcaCompletoQuandoTudoConfigurado() {
    Tenant tenant = tenantExistente();
    when(profissionalRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(servicoRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);
    when(profissionalRepository.countServiceAssignmentsByTenantId(tenantId)).thenReturn(1L);

    service.completeOnboarding(tenantId);

    assertThat(tenant.getOnboardingComplete()).isTrue();
    assertThat(tenant.getOnboardingCompletedAt()).isNotNull();
    verify(tenantRepository).save(tenant);
  }

  @Test
  void skipOnboardingMarcaSkippedETambemFalhaSeTenantAusente() {
    Tenant tenant = tenantExistente();

    service.skipOnboarding(tenantId);

    assertThat(tenant.getOnboardingSkipped()).isTrue();
    verify(tenantRepository).save(tenant);

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.skipOnboarding(tenantId)).isInstanceOf(ApiClientErrorException.class);
  }
}
