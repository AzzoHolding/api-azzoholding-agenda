package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PlanStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/**
 * Cobre {@code modules/billing/application/LicenseStatusService.java}: o status do plano vem dos
 * pedidos vigentes, o bloqueio exige tambem ausencia de assinatura ativa, e o {@code plan_status}
 * do tenant so e reescrito quando realmente mudou.
 */
class LicenseStatusServiceTest {

  private CheckoutOrderRepository checkoutOrderRepository;
  private SubscriptionRepository subscriptionRepository;
  private TenantRepository tenantRepository;
  private LicenseStatusService service;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    checkoutOrderRepository = mock(CheckoutOrderRepository.class);
    subscriptionRepository = mock(SubscriptionRepository.class);
    tenantRepository = mock(TenantRepository.class);
    when(tenantRepository.buscarCodigoPlanStatusPorTenant(any())).thenReturn(Optional.empty());
    service =
        new LicenseStatusService(checkoutOrderRepository, subscriptionRepository, tenantRepository);
  }

  @Test
  void planoVigenteResultaEmActive() {
    when(checkoutOrderRepository.possuiPlanoVigente(eq(tenantId), any())).thenReturn(true);
    when(subscriptionRepository.possuiRenovacaoAtiva(eq(tenantId))).thenReturn(false);

    LicenseStatusService.LicenseStatus status = service.avaliar(tenantId);

    assertThat(status.planStatus()).isEqualTo(PlanStatus.ACTIVE);
    assertThat(status.subscriptionActive()).isFalse();
  }

  @Test
  void semPlanoVigenteResultaEmExpiredESincronizaOTenant() {
    when(checkoutOrderRepository.possuiPlanoVigente(eq(tenantId), any())).thenReturn(false);
    when(tenantRepository.buscarCodigoPlanStatusPorTenant(eq(tenantId)))
        .thenReturn(Optional.of("ACTIVE"));

    LicenseStatusService.LicenseStatus status = service.avaliar(tenantId);

    assertThat(status.planStatus()).isEqualTo(PlanStatus.EXPIRED);
    verify(tenantRepository).atualizarPlanStatusPorCodigo(eq(tenantId), eq("EXPIRED"));
  }

  @Test
  void naoReescreveOPlanStatusQuandoJaEstaCorreto() {
    when(checkoutOrderRepository.possuiPlanoVigente(eq(tenantId), any())).thenReturn(true);
    // A coluna guarda a descricao em portugues; PlanStatus.fromValue aceita as duas grafias.
    when(tenantRepository.buscarCodigoPlanStatusPorTenant(eq(tenantId)))
        .thenReturn(Optional.of("Ativo"));

    service.avaliar(tenantId);

    verify(tenantRepository, never()).atualizarPlanStatusPorCodigo(any(), any());
  }

  @Test
  void deveBloquearSoQuandoVencidoESemRenovacaoAtiva() {
    when(checkoutOrderRepository.possuiPlanoVigente(eq(tenantId), any())).thenReturn(false);
    when(subscriptionRepository.possuiRenovacaoAtiva(eq(tenantId))).thenReturn(false);

    assertThat(service.deveBloquear(tenantId)).isTrue();
  }

  @Test
  void naoBloqueiaVencidoComAssinaturaAtivaRenovando() {
    when(checkoutOrderRepository.possuiPlanoVigente(eq(tenantId), any())).thenReturn(false);
    when(subscriptionRepository.possuiRenovacaoAtiva(eq(tenantId))).thenReturn(true);

    assertThat(service.deveBloquear(tenantId)).isFalse();
  }

  @Test
  void naoBloqueiaPlanoVigente() {
    when(checkoutOrderRepository.possuiPlanoVigente(eq(tenantId), any())).thenReturn(true);

    assertThat(service.deveBloquear(tenantId)).isFalse();
  }
}
