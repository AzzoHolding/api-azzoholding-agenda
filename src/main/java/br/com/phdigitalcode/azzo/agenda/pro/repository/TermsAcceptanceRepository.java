package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TermsAcceptance;

/** Espelha {@code modules/onboarding/domain/repository/TermsAcceptanceRepository.java}. */
@Repository
public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, UUID> {

  List<TermsAcceptance> findByTenantId(UUID tenantId);

  long countByTenantId(UUID tenantId);

  /** Espelha {@code find("tenantId = ?1 ORDER BY acceptedAt DESC").firstResultOptional()}. */
  Optional<TermsAcceptance> findFirstByTenantIdOrderByAcceptedAtDesc(UUID tenantId);

  default boolean hasAccepted(UUID tenantId) {
    return countByTenantId(tenantId) > 0;
  }

  default Optional<TermsAcceptance> findLatestByTenantId(UUID tenantId) {
    return findFirstByTenantIdOrderByAcceptedAtDesc(tenantId);
  }
}
