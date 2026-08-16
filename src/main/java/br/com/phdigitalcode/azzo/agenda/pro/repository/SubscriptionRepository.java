package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Subscription;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;

/** Espelha {@code modules/billing/domain/repository/SubscriptionRepository.java}. */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  List<Subscription> findByAsaasSubscriptionId(String asaasSubscriptionId);

  default Optional<Subscription> buscarPorAsaasSubscriptionId(String asaasSubscriptionId) {
    if (asaasSubscriptionId == null || asaasSubscriptionId.isBlank()) return Optional.empty();
    return findByAsaasSubscriptionId(asaasSubscriptionId.trim()).stream().findFirst();
  }

  /**
   * Ordena por relevancia de status (ACTIVE > PENDING > OVERDUE > CANCELLED > resto) e, dentro do
   * mesmo status, pela renovacao mais distante — igual ao {@code case when} do original.
   */
  @Query("""
      select s from Subscription s
      where s.tenantId = :tenantId
      order by
        case
          when s.status = :ativo then 0
          when s.status = :pendente then 1
          when s.status = :atrasado then 2
          when s.status = :cancelado then 3
          else 4
        end,
        s.nextDueDate desc nulls last,
        s.createdAt desc
      """)
  List<Subscription> listarPorRelevancia(
      @Param("tenantId") UUID tenantId,
      @Param("ativo") StatusSubscription ativo,
      @Param("pendente") StatusSubscription pendente,
      @Param("atrasado") StatusSubscription atrasado,
      @Param("cancelado") StatusSubscription cancelado);

  default Optional<Subscription> findCurrentByTenantId(UUID tenantId) {
    if (tenantId == null) return Optional.empty();
    return listarPorRelevancia(
            tenantId,
            StatusSubscription.ACTIVE,
            StatusSubscription.PENDING,
            StatusSubscription.OVERDUE,
            StatusSubscription.CANCELLED)
        .stream()
        .findFirst();
  }

  @Query("""
      select count(s) from Subscription s
      where s.tenantId = :tenantId and s.status = :status and s.cancelledAt is null
      """)
  long contarRenovacoesAtivas(
      @Param("tenantId") UUID tenantId, @Param("status") StatusSubscription status);

  default boolean possuiRenovacaoAtiva(UUID tenantId) {
    if (tenantId == null) return false;
    return contarRenovacoesAtivas(tenantId, StatusSubscription.ACTIVE) > 0;
  }

  /** Assinaturas vivas (ACTIVE/PENDING) que o admin marca como OVERDUE ao forcar vencimento. */
  @Query("""
      select s from Subscription s
      where s.tenantId = :tenantId and s.status in (:primeiro, :segundo) and s.cancelledAt is null
      """)
  List<Subscription> listarVivasPorStatus(
      @Param("tenantId") UUID tenantId,
      @Param("primeiro") StatusSubscription primeiro,
      @Param("segundo") StatusSubscription segundo);

  List<Subscription> findByTenantIdAndStatus(UUID tenantId, StatusSubscription status);
}
