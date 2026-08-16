package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;

/** Espelha {@code domain/repository/TenantRepository.java} (Panache -> Spring Data JPA + native queries). */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  java.util.List<Tenant> findByAsaasCustomerId(String asaasCustomerId);

  /**
   * Espelha {@code tenantRepository.find("slug", slug).firstResult()} de
   * {@code ServicoSalonProfile.obterPublico} do original.
   */
  Optional<Tenant> findBySlug(String slug);

  /**
   * Equivalente a {@code tenantRepository.find("asaasCustomerId", customer.trim()).firstResult()}
   * do {@code AsaasService}: identifica o tenant dono da cobranca a partir do customer do Asaas.
   */
  default Optional<Tenant> buscarPorAsaasCustomerId(String asaasCustomerId) {
    if (asaasCustomerId == null || asaasCustomerId.isBlank()) return Optional.empty();
    return findByAsaasCustomerId(asaasCustomerId.trim()).stream().findFirst();
  }

  @Query(value = "SELECT ps.id FROM plan_status ps WHERE ps.code = :code", nativeQuery = true)
  Optional<UUID> buscarPlanStatusIdPorCodigo(String code);

  @Query(
      value = "SELECT ps.code FROM tenants t JOIN plan_status ps ON ps.id = t.plan_status_id WHERE t.id = :tenantId",
      nativeQuery = true)
  Optional<String> buscarCodigoPlanStatusPorTenant(UUID tenantId);

  @Query(value = "SELECT t.document FROM tenants t WHERE t.id = :tenantId", nativeQuery = true)
  Optional<String> buscarDocumentoPorTenant(UUID tenantId);

  @Modifying
  @Transactional
  @Query(
      value = "UPDATE tenants t SET plan_status_id = (SELECT ps.id FROM plan_status ps WHERE ps.code = :code) "
          + "WHERE t.id = :tenantId AND EXISTS (SELECT 1 FROM plan_status ps WHERE ps.code = :code)",
      nativeQuery = true)
  void atualizarPlanStatusPorCodigo(UUID tenantId, String code);

  boolean existsByTrialDocumentHash(String trialDocumentHash);
}
