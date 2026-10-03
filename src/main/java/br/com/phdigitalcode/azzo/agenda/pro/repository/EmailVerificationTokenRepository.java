package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailVerificationToken;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

  @Query("select t from EmailVerificationToken t where t.tokenHash = :tokenHash and t.usedAt is null and t.expiresAt > :now")
  Optional<EmailVerificationToken> findActiveByHash(String tokenHash, Instant now);

  @Modifying
  @Transactional
  @Query("update EmailVerificationToken t set t.usedAt = :usedAt where t.userId = :userId and t.usedAt is null")
  void markAllActiveAsUsedByUser(UUID userId, Instant usedAt);
}
