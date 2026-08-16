package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.FiscalCertificateEntity;

/**
 * Espelha {@code modules/fiscal/domain/repository/FiscalCertificateRepository.java}.
 *
 * <p>{@code status} e comparado como texto ({@code "ACTIVE"} / {@code "INACTIVE"}), como no
 * original — a coluna nao e enum.
 */
@Repository
public interface FiscalCertificateRepository extends JpaRepository<FiscalCertificateEntity, UUID> {

  List<FiscalCertificateEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  Optional<FiscalCertificateEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<FiscalCertificateEntity> findByTenantIdAndStatus(UUID tenantId, String status);

  List<FiscalCertificateEntity> findByStatusAndValidToLessThanEqualOrderByValidToAsc(
      String status, Instant limit);

  /**
   * Update em massa, como o {@code update(...)} do Panache — nao carrega as entidades. Devolve
   * quantas linhas foram desativadas.
   */
  @Modifying
  @Query(
      "update FiscalCertificateEntity c set c.status = 'INACTIVE'"
          + " where c.tenantId = :tenantId and c.status = 'ACTIVE'")
  int desativarTodosAtivos(@Param("tenantId") UUID tenantId);

  default List<FiscalCertificateEntity> listByTenant(UUID tenantId) {
    return findByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  default Optional<FiscalCertificateEntity> findByTenantAndId(UUID tenantId, UUID id) {
    return findByTenantIdAndId(tenantId, id);
  }

  default Optional<FiscalCertificateEntity> findActiveByTenant(UUID tenantId) {
    return findByTenantIdAndStatus(tenantId, "ACTIVE");
  }

  default long deactivateAllActive(UUID tenantId) {
    return desativarTodosAtivos(tenantId);
  }

  /** Guarda do original: limite nulo devolve lista vazia sem consultar. */
  default List<FiscalCertificateEntity> listActiveExpiringUntil(Instant limit) {
    if (limit == null) return List.of();
    return findByStatusAndValidToLessThanEqualOrderByValidToAsc("ACTIVE", limit);
  }
}
