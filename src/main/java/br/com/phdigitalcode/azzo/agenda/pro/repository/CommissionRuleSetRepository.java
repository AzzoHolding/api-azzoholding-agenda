package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.CommissionRuleSet;

/** Espelha {@code modules/commission/domain/repository/CommissionRuleSetRepository.java}. */
@Repository
public interface CommissionRuleSetRepository extends JpaRepository<CommissionRuleSet, UUID> {

  Optional<CommissionRuleSet> findByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * Equivale ao {@code listByTenant(tenantId, professionalId, activeOnly)} do original — os dois
   * filtros opcionais viram comparacao com {@code null} no JPQL (o Panache montava a query em
   * runtime). Ordenacao identica: {@code scopeType asc, name asc, createdAt desc}.
   */
  @Query("""
      select rs from CommissionRuleSet rs
      where rs.tenantId = :tenantId
        and (:professionalId is null or rs.professionalId = :professionalId)
        and (:activeOnly = false or rs.active = true)
      order by rs.scopeType asc, rs.name asc, rs.createdAt desc
      """)
  List<CommissionRuleSet> listByTenant(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("activeOnly") boolean activeOnly);

  @Query("""
      select rs from CommissionRuleSet rs
      where rs.tenantId = :tenantId
        and rs.active = true
        and rs.scopeType = 'PROFESSIONAL'
        and rs.professionalId = :professionalId
      order by rs.updatedAt desc
      """)
  List<CommissionRuleSet> listActiveProfessionalScoped(
      @Param("tenantId") UUID tenantId, @Param("professionalId") UUID professionalId);

  @Query("""
      select rs from CommissionRuleSet rs
      where rs.tenantId = :tenantId
        and rs.active = true
        and rs.scopeType = 'GLOBAL'
        and rs.professionalId is null
      order by rs.updatedAt desc
      """)
  List<CommissionRuleSet> listActiveGlobalScoped(@Param("tenantId") UUID tenantId);

  /**
   * Desativa os demais rule sets do mesmo escopo (so pode existir um ativo por escopo).
   * Replica o {@code deactivateConflictingRuleSets} do original, incluindo o tratamento de
   * {@code professionalId is null} para o escopo GLOBAL.
   */
  @Modifying
  @Query("""
      update CommissionRuleSet rs
      set rs.active = false
      where rs.tenantId = :tenantId
        and rs.scopeType = :scopeType
        and rs.id <> :excludeId
        and (
          (:professionalId is null and rs.professionalId is null)
          or rs.professionalId = :professionalId
        )
      """)
  int deactivateConflicting(
      @Param("tenantId") UUID tenantId,
      @Param("scopeType") String scopeType,
      @Param("excludeId") UUID excludeId,
      @Param("professionalId") UUID professionalId);
}
