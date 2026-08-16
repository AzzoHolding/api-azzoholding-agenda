package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ServiceCategory;

/** Espelha {@code modules/services/domain/repository/ServiceCategoryRepository.java}. */
@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {

  @Query("select c from ServiceCategory c where c.tenantId = :tenantId and lower(c.name) = lower(:name)")
  Optional<ServiceCategory> findByTenantAndName(UUID tenantId, String name);
}
