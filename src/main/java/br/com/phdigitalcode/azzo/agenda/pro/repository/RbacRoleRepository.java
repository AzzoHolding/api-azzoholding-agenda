package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacRole;

/** Espelha {@code modules/auth/domain/repository/RbacRoleRepository.java}. */
@Repository
public interface RbacRoleRepository extends JpaRepository<RbacRole, UUID> {

  @Query("select r from RbacRole r where lower(r.name) = lower(:name)")
  Optional<RbacRole> findByNameIgnoreCase(String name);
}
