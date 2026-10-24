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

  /**
   * Solta o documento do periodo gratuito (anula o hash) de salao cujo cadastro NUNCA foi
   * confirmado: criado antes de {@code limite} e com TODOS os usuarios ainda com o e-mail
   * pendente. Nao apaga nada (achado SEG-009, auditoria de 2026-10-06).
   *
   * <p>O periodo gratuito e um por documento, e o documento era reservado ja no cadastro, antes de
   * provar a posse do e-mail: quem se cadastrava com o CNPJ de um concorrente e um e-mail
   * descartavel nunca confirmava, e o concorrente passava a receber "ja utilizou o plano gratuito".
   * Idempotente, entao rodar em mais de uma instancia e seguro.
   *
   * @return quantos saloes tiveram o documento liberado
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "UPDATE tenants t SET trial_document_hash = NULL "
              + "WHERE t.trial_document_hash IS NOT NULL "
              + "AND t.created_at < :limite "
              + "AND EXISTS (SELECT 1 FROM users u "
              + "            WHERE u.tenant_id = t.id AND u.email_confirmation_pending = TRUE) "
              + "AND NOT EXISTS (SELECT 1 FROM users u "
              + "                WHERE u.tenant_id = t.id AND u.email_confirmation_pending IS NOT TRUE)",
      nativeQuery = true)
  int liberarDocumentoDeTrialDeCadastrosNaoConfirmados(java.time.Instant limite);
}
