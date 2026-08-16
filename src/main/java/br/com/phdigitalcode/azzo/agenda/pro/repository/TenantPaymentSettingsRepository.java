package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantPaymentSettings;

/** Espelha {@code domain/repository/TenantPaymentSettingsRepository.java}. */
@Repository
public interface TenantPaymentSettingsRepository extends JpaRepository<TenantPaymentSettings, UUID> {

  Optional<TenantPaymentSettings> findByWebhookToken(String webhookToken);

  boolean existsByWebhookToken(String webhookToken);

  default Optional<TenantPaymentSettings> findByTenantId(UUID tenantId) {
    return tenantId == null ? Optional.empty() : findById(tenantId);
  }
}
