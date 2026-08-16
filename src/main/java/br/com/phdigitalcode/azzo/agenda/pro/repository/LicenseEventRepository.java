package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.LicenseEvent;

/** Espelha {@code modules/billing/domain/repository/LicenseEventRepository.java}. */
@Repository
public interface LicenseEventRepository extends JpaRepository<LicenseEvent, UUID> {

  List<LicenseEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /** Nome do original ({@code list("tenantId = ?1 order by createdAt desc")}). */
  default List<LicenseEvent> findByTenant(UUID tenantId) {
    if (tenantId == null) return List.of();
    return findByTenantIdOrderByCreatedAtDesc(tenantId);
  }
}
