package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditRetentionEvent;

/** Espelha {@code modules/audit/domain/repository/AuditRetentionEventRepository.java}. */
@Repository
public interface AuditRetentionEventRepository
    extends JpaRepository<AuditRetentionEvent, UUID>, JpaSpecificationExecutor<AuditRetentionEvent> {

  @Query(
      "select count(e) > 0 from AuditRetentionEvent e where (e.tenantId = :tenantId or e.tenantId is null) "
          + "and e.windowEnd <= :createdAt and e.affectedRows > 0")
  boolean existsPurgeBoundaryBeforeRaw(@Param("tenantId") UUID tenantId, @Param("createdAt") Instant createdAt);

  /** Espelha {@code auditRetentionEventRepository.existsPurgeBoundaryBefore(tenantId, createdAt)}. */
  default boolean existsPurgeBoundaryBefore(UUID tenantId, Instant createdAt) {
    if (tenantId == null || createdAt == null) return false;
    return existsPurgeBoundaryBeforeRaw(tenantId, createdAt);
  }
}
