package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.MembershipPlanBenefit;

/** Espelha {@code modules/membership/domain/repository/MembershipPlanBenefitRepository.java}. */
@Repository
public interface MembershipPlanBenefitRepository extends JpaRepository<MembershipPlanBenefit, UUID> {

  List<MembershipPlanBenefit> findByPlanId(UUID planId);

  /** Equivalente ao {@code delete("planId", id)} do Panache: o {@code atualizar} regrava os beneficios do zero. */
  void deleteByPlanId(UUID planId);
}
