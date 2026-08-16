package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseCertificateUnlockSessionEntity;

/** Espelha {@code modules/nfse/domain/repository/NfseCertificateUnlockSessionRepository.java}. */
@Repository
public interface NfseCertificateUnlockSessionRepository
    extends JpaRepository<NfseCertificateUnlockSessionEntity, UUID> {

  Optional<NfseCertificateUnlockSessionEntity>
      findFirstByTenantIdAndUserIdAndStatusAndExpiresAtAfterOrderByIssuedAtDesc(
          UUID tenantId, UUID userId, String status, Instant now);

  Optional<NfseCertificateUnlockSessionEntity> findByTenantIdAndUserIdAndUnlockTokenIdAndStatus(
      UUID tenantId, UUID userId, String unlockTokenId, String status);

  @Modifying
  @Query(
      "update NfseCertificateUnlockSessionEntity s set s.status = 'REVOKED', s.revokedAt = :now"
          + " where s.tenantId = :tenantId and s.userId = :userId and s.status = 'ACTIVE'")
  int revokeActiveSessions(
      @Param("now") Instant now, @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

  @Modifying
  @Query(
      "update NfseCertificateUnlockSessionEntity s set s.status = 'EXPIRED'"
          + " where s.status = 'ACTIVE' and s.expiresAt <= :now")
  int expireActiveSessionsPastDue(@Param("now") Instant now);

  default Optional<NfseCertificateUnlockSessionEntity> findActive(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) return Optional.empty();
    return findFirstByTenantIdAndUserIdAndStatusAndExpiresAtAfterOrderByIssuedAtDesc(
        tenantId, userId, "ACTIVE", Instant.now());
  }

  default long revokeActive(UUID tenantId, UUID userId) {
    if (tenantId == null || userId == null) return 0;
    return revokeActiveSessions(Instant.now(), tenantId, userId);
  }

  default Optional<NfseCertificateUnlockSessionEntity> findByToken(
      UUID tenantId, UUID userId, String unlockTokenId) {
    if (tenantId == null || userId == null || unlockTokenId == null || unlockTokenId.isBlank()) {
      return Optional.empty();
    }
    return findByTenantIdAndUserIdAndUnlockTokenIdAndStatus(
        tenantId, userId, unlockTokenId.trim(), "ACTIVE");
  }

  default long expirePastDueSessions() {
    return expireActiveSessionsPastDue(Instant.now());
  }
}
