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

  /** A ancora mais recente do salao: onde a cadeia selada (v2) continua depois do ultimo expurgo. */
  @Query(
      "select e from AuditRetentionEvent e where e.tenantId = :tenantId and e.chainAnchorSeq is not null "
          + "order by e.chainAnchorSeq desc, e.createdAt desc")
  java.util.List<AuditRetentionEvent> findAncorasSeladas(
      @Param("tenantId") UUID tenantId, org.springframework.data.domain.Limit limit);

  default java.util.Optional<AuditRetentionEvent> ultimaAncoraSelada(UUID tenantId) {
    return findAncorasSeladas(tenantId, org.springframework.data.domain.Limit.of(1)).stream().findFirst();
  }

  /** Todos os hashes de ancora do salao (legado e v2): elos que o expurgo removeu de proposito. */
  @Query(
      "select e.chainAnchorHash from AuditRetentionEvent e where e.tenantId = :tenantId "
          + "and e.chainAnchorHash is not null order by e.createdAt asc")
  java.util.List<String> hashesDeAncora(@Param("tenantId") UUID tenantId);

  @Query("select count(e) > 0 from AuditRetentionEvent e where e.tenantId = :tenantId and e.affectedRows > 0")
  boolean houveExpurgo(@Param("tenantId") UUID tenantId);

  /** Espelha {@code auditRetentionEventRepository.existsPurgeBoundaryBefore(tenantId, createdAt)}. */
  default boolean existsPurgeBoundaryBefore(UUID tenantId, Instant createdAt) {
    if (tenantId == null || createdAt == null) return false;
    return existsPurgeBoundaryBeforeRaw(tenantId, createdAt);
  }
}
