package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import br.com.phdigitalcode.azzo.agenda.pro.specification.WhatsAppBookingReactivationCycleSpecifications;

/** Espelha {@code modules/chat/domain/repository/WhatsAppBookingReactivationCycleRepository.java}. */
@Repository
public interface WhatsAppBookingReactivationCycleRepository
    extends JpaRepository<WhatsAppBookingReactivationCycleEntity, UUID>,
        JpaSpecificationExecutor<WhatsAppBookingReactivationCycleEntity> {

  @Query(
      "from WhatsAppBookingReactivationCycleEntity c where c.tenantId = :tenantId "
          + "and c.userIdentifier = :userIdentifier and c.status in :statuses order by c.updatedAt desc")
  List<WhatsAppBookingReactivationCycleEntity> listLatestByTenantAndUserIdentifier(
      @Param("tenantId") UUID tenantId,
      @Param("userIdentifier") String userIdentifier,
      @Param("statuses") List<WhatsAppBookingReactivationStatus> statuses,
      Pageable pageable);

  @Query(
      "from WhatsAppBookingReactivationCycleEntity c where c.tenantId = :tenantId "
          + "and c.clientId = :clientId and c.status in :statuses order by c.updatedAt desc")
  List<WhatsAppBookingReactivationCycleEntity> listLatestByTenantAndClient(
      @Param("tenantId") UUID tenantId,
      @Param("clientId") UUID clientId,
      @Param("statuses") List<WhatsAppBookingReactivationStatus> statuses,
      Pageable pageable);

  @Query(
      "from WhatsAppBookingReactivationCycleEntity c where c.tenantId = :tenantId "
          + "and c.userIdentifier in :userIdentifiers and c.status in :statuses order by c.updatedAt desc")
  List<WhatsAppBookingReactivationCycleEntity> listLatestByTenantAndUserIdentifiers(
      @Param("tenantId") UUID tenantId,
      @Param("userIdentifiers") List<String> userIdentifiers,
      @Param("statuses") List<WhatsAppBookingReactivationStatus> statuses,
      Pageable pageable);

  @Query(
      "from WhatsAppBookingReactivationCycleEntity c where c.status = :status "
          + "and c.nextAttemptAt is not null and c.nextAttemptAt <= :now order by c.nextAttemptAt asc")
  List<WhatsAppBookingReactivationCycleEntity> listDue(
      @Param("status") WhatsAppBookingReactivationStatus status, @Param("now") Instant now);

  @Query(
      "from WhatsAppBookingReactivationCycleEntity c where c.tenantId = :tenantId "
          + "and c.clientId = :clientId and c.status in :statuses order by c.updatedAt desc")
  List<WhatsAppBookingReactivationCycleEntity> listActiveByTenantAndClientRaw(
      @Param("tenantId") UUID tenantId,
      @Param("clientId") UUID clientId,
      @Param("statuses") List<WhatsAppBookingReactivationStatus> statuses);

  /**
   * Espelha {@code tenantId = ?1 AND createdAt >= ?2 AND createdAt <= ?3} de
   * {@code ReactivationConfigService.getMetrics} do original.
   */
  List<WhatsAppBookingReactivationCycleEntity> findByTenantIdAndCreatedAtBetween(
      UUID tenantId, Instant from, Instant to);

  /**
   * Espelha {@code tenantId = ?1 AND reactivatedAt >= ?2 AND reactivatedAt <= ?3 AND
   * nextAttemptNumber > ?4} de {@code ReactivationConfigService.buildByAttempt} do original.
   */
  long countByTenantIdAndReactivatedAtBetweenAndNextAttemptNumberGreaterThan(
      UUID tenantId, Instant from, Instant to, int attemptNumber);

  default Optional<WhatsAppBookingReactivationCycleEntity> findLatestByTenantAndUserIdentifier(
      UUID tenantId, String userIdentifier, List<WhatsAppBookingReactivationStatus> statuses) {
    if (tenantId == null
        || userIdentifier == null
        || userIdentifier.isBlank()
        || statuses == null
        || statuses.isEmpty()) {
      return Optional.empty();
    }
    List<WhatsAppBookingReactivationCycleEntity> result =
        listLatestByTenantAndUserIdentifier(tenantId, userIdentifier, statuses, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  default Optional<WhatsAppBookingReactivationCycleEntity> findLatestByTenantAndClient(
      UUID tenantId, UUID clientId, List<WhatsAppBookingReactivationStatus> statuses) {
    if (tenantId == null || clientId == null || statuses == null || statuses.isEmpty()) {
      return Optional.empty();
    }
    List<WhatsAppBookingReactivationCycleEntity> result =
        listLatestByTenantAndClient(tenantId, clientId, statuses, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  default Optional<WhatsAppBookingReactivationCycleEntity> findLatestByTenantAndUserIdentifiers(
      UUID tenantId, List<String> userIdentifiers, List<WhatsAppBookingReactivationStatus> statuses) {
    if (tenantId == null
        || userIdentifiers == null
        || userIdentifiers.isEmpty()
        || statuses == null
        || statuses.isEmpty()) {
      return Optional.empty();
    }
    List<WhatsAppBookingReactivationCycleEntity> result =
        listLatestByTenantAndUserIdentifiers(tenantId, userIdentifiers, statuses, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  default List<WhatsAppBookingReactivationCycleEntity> listDue(Instant now) {
    return listDue(WhatsAppBookingReactivationStatus.ACTIVE, now);
  }

  default List<WhatsAppBookingReactivationCycleEntity> listActiveByTenantAndClient(
      UUID tenantId, UUID clientId) {
    if (tenantId == null || clientId == null) return List.of();
    return listActiveByTenantAndClientRaw(
        tenantId,
        clientId,
        List.of(
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStatus.REACTIVATED));
  }

  default List<WhatsAppBookingReactivationCycleEntity> listOperational(
      UUID tenantId,
      Instant abandonedAfter,
      WhatsAppBookingReactivationStatus status,
      int limit) {
    return listOperational(tenantId, abandonedAfter, null, status, null, null, 0, limit);
  }

  default List<WhatsAppBookingReactivationCycleEntity> listOperational(
      UUID tenantId,
      Instant abandonedAfter,
      Instant abandonedBefore,
      WhatsAppBookingReactivationStatus status,
      WhatsAppBookingReactivationStage stage,
      String searchTerm,
      int pageIndex,
      int pageSize) {
    if (tenantId == null) return List.of();
    int normalizedPageIndex = Math.max(pageIndex, 0);
    int normalizedPageSize = pageSize <= 0 ? 12 : Math.min(pageSize, 50);
    Pageable pageable =
        PageRequest.of(normalizedPageIndex, normalizedPageSize, Sort.by(Sort.Direction.DESC, "abandonedAt"));
    return findAll(
            WhatsAppBookingReactivationCycleSpecifications.operational(
                tenantId, abandonedAfter, abandonedBefore, status, stage, searchTerm),
            pageable)
        .getContent();
  }

  default long countOperational(
      UUID tenantId,
      Instant abandonedAfter,
      Instant abandonedBefore,
      WhatsAppBookingReactivationStatus status,
      WhatsAppBookingReactivationStage stage,
      String searchTerm) {
    if (tenantId == null) return 0L;
    return count(
        WhatsAppBookingReactivationCycleSpecifications.operational(
            tenantId, abandonedAfter, abandonedBefore, status, stage, searchTerm));
  }
}
