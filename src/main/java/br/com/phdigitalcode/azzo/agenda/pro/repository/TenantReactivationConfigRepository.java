package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantReactivationConfigEntity;

/** Espelha {@code modules/chat/domain/repository/TenantReactivationConfigRepository.java}. */
@Repository
public interface TenantReactivationConfigRepository
    extends JpaRepository<TenantReactivationConfigEntity, UUID> {

  Optional<TenantReactivationConfigEntity> findByTenantId(UUID tenantId);

  default TenantReactivationConfigEntity findByTenantIdOrDefault(UUID tenantId) {
    return findByTenantId(tenantId)
        .orElseGet(
            () -> {
              TenantReactivationConfigEntity config = new TenantReactivationConfigEntity();
              config.setTenantId(tenantId);
              return config;
            });
  }
}
