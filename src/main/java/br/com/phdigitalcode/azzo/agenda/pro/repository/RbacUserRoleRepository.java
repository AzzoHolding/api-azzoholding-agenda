package br.com.phdigitalcode.azzo.agenda.pro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.RbacUserRole;
import br.com.phdigitalcode.azzo.agenda.pro.entity.id.RbacUserRoleId;

/** Espelha {@code modules/auth/domain/repository/RbacUserRoleRepository.java}. */
@Repository
public interface RbacUserRoleRepository extends JpaRepository<RbacUserRole, RbacUserRoleId> {
}
