package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.PasswordResetToken;

/** Espelha {@code modules/auth/domain/repository/PasswordResetTokenRepository.java}. */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now")
  Optional<PasswordResetToken> findActiveByHash(String tokenHash, Instant now);

  @Modifying
  @Transactional
  @Query("update PasswordResetToken t set t.usedAt = :usedAt where t.userId = :userId and t.usedAt is null")
  void markAllActiveAsUsedByUser(UUID userId, Instant usedAt);
}
