package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;

/** Espelha {@code modules/professionals/domain/repository/ProfissionalRepository.java}. */
@Repository
public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

  List<Profissional> findByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<Profissional> findByIdAndTenantId(UUID id, UUID tenantId);

  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<Profissional> findByTenantIdAndUserId(UUID tenantId, UUID userId);

  /** Espelha o {@code find("id = ?1 and tenantId = ?2 and isActive = true")} de scheduling. */
  Optional<Profissional> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);

  List<Profissional> findByIdInAndTenantId(List<UUID> ids, UUID tenantId);

  /**
   * Espelha a native query de {@code ServicoOnboarding.contarVinculosProfissionalServico}: conta
   * vinculos profissional-servico (tabela {@code service_professionals}) para profissionais ativos
   * do tenant.
   */
  @Query(value = "SELECT COUNT(*) FROM service_professionals sp "
      + "INNER JOIN professionals p ON p.id = sp.professional_id "
      + "WHERE p.tenant_id = :tenantId AND p.is_active = true", nativeQuery = true)
  long countServiceAssignmentsByTenantId(@Param("tenantId") UUID tenantId);
}
