package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationSendLogEntity;

/** Espelha {@code modules/chat/domain/repository/ReactivationSendLogRepository.java}. */
@Repository
public interface ReactivationSendLogRepository
    extends JpaRepository<ReactivationSendLogEntity, UUID> {

  long countByClientIdAndSentAtGreaterThanEqual(UUID clientId, Instant after);

  /**
   * Espelha {@code tenantId = ?1 AND attemptNumber = ?2 AND sentAt >= ?3 AND sentAt <= ?4} de
   * {@code ReactivationConfigService.buildByAttempt} do original.
   */
  long countByTenantIdAndAttemptNumberAndSentAtBetween(
      UUID tenantId, int attemptNumber, Instant from, Instant to);

  @Query(
      "from ReactivationSendLogEntity l where l.clientId = :clientId order by l.sentAt desc")
  List<ReactivationSendLogEntity> listLatestByClientId(
      @Param("clientId") UUID clientId, org.springframework.data.domain.Pageable pageable);

  @Transactional
  long deleteByExpiresAtLessThan(Instant now);

  default long countByClientIdAndSentAtAfter(UUID clientId, Instant after) {
    return countByClientIdAndSentAtGreaterThanEqual(clientId, after);
  }

  default Optional<ReactivationSendLogEntity> findLastByClientId(UUID clientId) {
    List<ReactivationSendLogEntity> result = listLatestByClientId(clientId, PageRequest.of(0, 1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  default long deleteExpired(Instant now) {
    return deleteByExpiresAtLessThan(now);
  }
}
