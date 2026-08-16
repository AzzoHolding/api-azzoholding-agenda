package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PlanStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.CheckoutOrderRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.SubscriptionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;

/**
 * Espelha {@code modules/billing/application/LicenseStatusService.java}.
 *
 * <p>Avaliar a licenca <b>escreve</b>: o {@code plan_status_id} do tenant e sincronizado com o
 * status calculado a partir dos pedidos vigentes. Por isso {@code avaliar} e {@code @Transactional}
 * de escrita, mesmo parecendo uma consulta.
 */
@Service
public class LicenseStatusService {

  private final CheckoutOrderRepository checkoutOrderRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final TenantRepository tenantRepository;

  public LicenseStatusService(
      CheckoutOrderRepository checkoutOrderRepository,
      SubscriptionRepository subscriptionRepository,
      TenantRepository tenantRepository) {
    this.checkoutOrderRepository = checkoutOrderRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.tenantRepository = tenantRepository;
  }

  @Transactional
  public LicenseStatus avaliar(UUID tenantId) {
    boolean planoVigente = checkoutOrderRepository.possuiPlanoVigente(tenantId, Instant.now());
    boolean subscriptionActive = subscriptionRepository.possuiRenovacaoAtiva(tenantId);
    PlanStatus planStatus = planoVigente ? PlanStatus.ACTIVE : PlanStatus.EXPIRED;
    sincronizarPlanStatusTenant(tenantId, planStatus);
    return new LicenseStatus(planStatus, subscriptionActive);
  }

  /**
   * Bloqueia apenas quando o plano venceu <b>e</b> nao ha assinatura ativa renovando — quem tem
   * renovacao em curso continua acessando mesmo com o pedido vencido.
   */
  public boolean deveBloquear(UUID tenantId) {
    LicenseStatus status = avaliar(tenantId);
    return PlanStatus.EXPIRED.equals(status.planStatus()) && !status.subscriptionActive();
  }

  private void sincronizarPlanStatusTenant(UUID tenantId, PlanStatus statusCalculado) {
    if (tenantId == null || statusCalculado == null) return;
    PlanStatus atual =
        tenantRepository
            .buscarCodigoPlanStatusPorTenant(tenantId)
            .map(PlanStatus::fromValue)
            .orElse(null);
    if (atual != statusCalculado) {
      tenantRepository.atualizarPlanStatusPorCodigo(tenantId, statusCalculado.name());
    }
  }

  public record LicenseStatus(PlanStatus planStatus, boolean subscriptionActive) {}
}
