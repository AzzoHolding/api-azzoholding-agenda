package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionRule;

/** Espelha {@code modules/commission/domain/repository/CommissionRuleRepository.java}. */
@Repository
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {

  @Query("""
      select r from CommissionRule r
      where r.tenantId = :tenantId and r.ruleSetId = :ruleSetId
      order by r.targetType asc, r.targetCode asc nulls last, r.targetId asc nulls last
      """)
  List<CommissionRule> listByRuleSet(
      @Param("tenantId") UUID tenantId, @Param("ruleSetId") UUID ruleSetId);

  @Modifying
  @Query("delete from CommissionRule r where r.tenantId = :tenantId and r.ruleSetId = :ruleSetId")
  int deleteByRuleSet(@Param("tenantId") UUID tenantId, @Param("ruleSetId") UUID ruleSetId);
}
