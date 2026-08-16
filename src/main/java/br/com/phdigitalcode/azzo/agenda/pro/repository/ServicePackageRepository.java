package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ServicePackage;

/** Espelha {@code modules/packages/domain/repository/ServicePackageRepository.java}. */
@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {

  List<ServicePackage> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<ServicePackage> findByIdAndTenantId(UUID id, UUID tenantId);
}
