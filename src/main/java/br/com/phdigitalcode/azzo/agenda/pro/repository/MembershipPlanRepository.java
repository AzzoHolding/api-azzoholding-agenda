package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlan;

/** Espelha {@code modules/membership/domain/repository/MembershipPlanRepository.java}. */
@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {

  List<MembershipPlan> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<MembershipPlan> findByIdAndTenantId(UUID id, UUID tenantId);
}
