package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Specialty;

/** Espelha {@code modules/services/domain/repository/SpecialtyRepository.java}. */
@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, UUID> {

  @Query("select s from Specialty s where s.tenantId = :tenantId and lower(s.name) = lower(:name)")
  Optional<Specialty> findByTenantAndName(UUID tenantId, String name);

  List<Specialty> findByTenantIdOrderByName(UUID tenantId);
}
