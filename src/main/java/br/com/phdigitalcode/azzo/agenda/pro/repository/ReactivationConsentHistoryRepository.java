package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationConsentHistoryEntity;

/** Espelha {@code modules/chat/domain/repository/ReactivationConsentHistoryRepository.java}. */
@Repository
public interface ReactivationConsentHistoryRepository
    extends JpaRepository<ReactivationConsentHistoryEntity, UUID> {

  List<ReactivationConsentHistoryEntity> findByTenantIdAndClientIdOrderByCreatedAtDesc(
      UUID tenantId, UUID clientId);

  default List<ReactivationConsentHistoryEntity> findByClientId(UUID tenantId, UUID clientId) {
    return findByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId);
  }

  /**
   * Espelha a query usada por {@code ReactivationConfigService.listOptOutsPaged} do original:
   * {@code clientId in ?1 and tenantId = ?2 and action = 'OPT_OUT' order by createdAt desc}.
   */
  List<ReactivationConsentHistoryEntity> findByClientIdInAndTenantIdAndActionOrderByCreatedAtDesc(
      List<UUID> clientIds, UUID tenantId, String action);
}
