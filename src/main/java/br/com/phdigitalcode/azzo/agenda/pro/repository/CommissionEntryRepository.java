package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionEntry;

/** Espelha {@code modules/commission/domain/repository/CommissionEntryRepository.java}. */
@Repository
public interface CommissionEntryRepository extends JpaRepository<CommissionEntry, UUID> {

  @Query("""
      select e from CommissionEntry e
      where e.tenantId = :tenantId and e.originType = :originType and e.originId = :originId
      order by e.createdAt desc, e.id desc
      """)
  List<CommissionEntry> findByTenantAndOrigin(
      @Param("tenantId") UUID tenantId,
      @Param("originType") String originType,
      @Param("originId") UUID originId,
      Limit limit);

  default Optional<CommissionEntry> findByTenantAndOrigin(UUID tenantId, String originType, UUID originId) {
    return findByTenantAndOrigin(tenantId, originType, originId, Limit.of(1)).stream().findFirst();
  }

  @Query("""
      select e from CommissionEntry e
      where e.tenantId = :tenantId
        and e.originType = :originType
        and e.originId = :originId
        and e.entryStatus <> 'REVERSED'
      order by e.createdAt desc, e.id desc
      """)
  List<CommissionEntry> findNonReversedByTenantAndOrigin(
      @Param("tenantId") UUID tenantId,
      @Param("originType") String originType,
      @Param("originId") UUID originId,
      Limit limit);

  default Optional<CommissionEntry> findLatestNonReversedByTenantAndOrigin(
      UUID tenantId, String originType, UUID originId) {
    return findNonReversedByTenantAndOrigin(tenantId, originType, originId, Limit.of(1)).stream().findFirst();
  }

  @Query("""
      select e from CommissionEntry e
      where e.tenantId = :tenantId
        and e.originType = :originType
        and e.originReference like concat(:prefix, '%')
      order by e.createdAt desc, e.id desc
      """)
  List<CommissionEntry> listByTenantAndOriginReferencePrefix(
      @Param("tenantId") UUID tenantId,
      @Param("originType") String originType,
      @Param("prefix") String prefix,
      Limit limit);

  default List<CommissionEntry> listByTenantAndOriginReferencePrefix(
      UUID tenantId, String originType, String prefix) {
    return listByTenantAndOriginReferencePrefix(tenantId, originType, prefix, Limit.of(1000));
  }

  @Query("""
      select e from CommissionEntry e
      where e.tenantId = :tenantId
        and e.professionalId = :professionalId
        and e.createdAt >= :fromInclusive
        and e.createdAt < :toExclusive
      order by e.createdAt desc, e.id desc
      """)
  List<CommissionEntry> listByTenantAndProfessionalAndCreatedAtRange(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive,
      Limit limit);

  default List<CommissionEntry> listByTenantAndProfessionalAndCreatedAtRange(
      UUID tenantId, UUID professionalId, Instant fromInclusive, Instant toExclusive) {
    return listByTenantAndProfessionalAndCreatedAtRange(
        tenantId, professionalId, fromInclusive, toExclusive, Limit.of(1000));
  }

  /** Entradas abertas ainda sem ciclo, no periodo — usadas no fechamento do ciclo. */
  @Query("""
      select e from CommissionEntry e
      where e.tenantId = :tenantId
        and e.entryStatus = 'OPEN'
        and e.cycleId is null
        and e.createdAt >= :fromStart
        and e.createdAt < :toExclusive
      order by e.createdAt asc, e.id asc
      """)
  List<CommissionEntry> listOpenWithoutCycleInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("fromStart") Instant fromStart,
      @Param("toExclusive") Instant toExclusive);

  @Query("""
      select e.professionalId, sum(e.totalAmountCents)
      from CommissionEntry e
      where e.tenantId = :tenantId and e.cycleId = :cycleId and e.entryStatus <> 'REVERSED'
      group by e.professionalId
      """)
  List<Object[]> sumTotalCentsByProfessionalForCycle(
      @Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

  @Modifying
  @Query("""
      update CommissionEntry e
      set e.entryStatus = 'PAID'
      where e.tenantId = :tenantId and e.cycleId = :cycleId and e.entryStatus <> 'REVERSED'
      """)
  int markCycleEntriesAsPaid(@Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

  long countByCycleId(UUID cycleId);
}
