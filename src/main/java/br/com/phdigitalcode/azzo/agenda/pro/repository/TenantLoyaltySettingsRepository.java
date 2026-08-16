package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantLoyaltySettings;

/** Espelha {@code domain/repository/TenantLoyaltySettingsRepository.java}. */
@Repository
public interface TenantLoyaltySettingsRepository extends JpaRepository<TenantLoyaltySettings, UUID> {

  default Optional<TenantLoyaltySettings> findByTenantId(UUID tenantId) {
    return tenantId == null ? Optional.empty() : findById(tenantId);
  }
}
