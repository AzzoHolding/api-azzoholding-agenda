package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RefreshToken;

/** Espelha {@code modules/auth/domain/repository/RefreshTokenRepository.java}. */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  @Query("select t from RefreshToken t where t.tokenHash = :tokenHash and t.revokedAt is null and t.expiresAt > :now")
  Optional<RefreshToken> findActiveByHash(String tokenHash, Instant now);

  @Modifying
  @Transactional
  @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
  long revokeAllByUser(UUID userId, Instant now);

  @Modifying
  @Transactional
  @Query("update RefreshToken t set t.revokedAt = :now where t.tenantId = :tenantId and t.revokedAt is null")
  long revokeAllByTenant(UUID tenantId, Instant now);
}
