package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.LgpdDataSubjectRequest;

/**
 * Espelha {@code modules/lgpd/domain/repository/LgpdDataSubjectRequestRepository.java}. O
 * {@code listByTenant} do original monta HQL dinamicamente conforme os filtros opcionais que
 * vieram preenchidos — aqui isso vira {@link JpaSpecificationExecutor} (mesmo padrao ja usado em
 * {@code AuditEventRepository}/{@code TenantSpecialClosureDateRepository}), em vez de JPQL com
 * {@code :param is null} (que quebra no PostgreSQL com "could not determine data type of
 * parameter" quando o bind e nulo).
 */
@Repository
public interface LgpdDataSubjectRequestRepository
    extends JpaRepository<LgpdDataSubjectRequest, UUID>,
        JpaSpecificationExecutor<LgpdDataSubjectRequest> {

  Optional<LgpdDataSubjectRequest> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<LgpdDataSubjectRequest> findByTenantIdAndProtocolCode(UUID tenantId, String protocolCode);

  /** Espelha {@code requestRepository.findByTenantAndId(tenantId, id)}. */
  default Optional<LgpdDataSubjectRequest> findByTenantAndId(UUID tenantId, UUID id) {
    return findByTenantIdAndId(tenantId, id);
  }

  /** Espelha {@code requestRepository.findByTenantAndProtocol(tenantId, protocolCode)}. */
  default Optional<LgpdDataSubjectRequest> findByTenantAndProtocol(UUID tenantId, String protocolCode) {
    return findByTenantIdAndProtocolCode(tenantId, protocolCode);
  }

  /**
   * Espelha {@code requestRepository.listByTenant(tenantId, status, requestType, limit)}: filtros
   * opcionais + normalizacao de limite (padrao 50, teto 200) igual ao original.
   */
  default List<LgpdDataSubjectRequest> listByTenant(
      UUID tenantId, String status, String requestType, Integer limit) {
    int normalizedLimit = Math.max(1, Math.min(limit == null ? 50 : limit, 200));
    org.springframework.data.jpa.domain.Specification<LgpdDataSubjectRequest> spec =
        (root, query, cb) -> {
          List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
          predicates.add(cb.equal(root.get("tenantId"), tenantId));
          if (status != null && !status.isBlank()) {
            predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
          }
          if (requestType != null && !requestType.isBlank()) {
            predicates.add(cb.equal(cb.upper(root.get("requestType")), requestType.trim().toUpperCase()));
          }
          return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    return findAll(
            spec,
            PageRequest.of(0, normalizedLimit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
        .getContent();
  }

  long countByTenantIdAndStatusIgnoreCase(UUID tenantId, String status);

  /** Espelha {@code requestRepository.countByTenantAndStatus(tenantId, status)}. */
  default long countByTenantAndStatus(UUID tenantId, String status) {
    return countByTenantIdAndStatusIgnoreCase(tenantId, status.trim().toUpperCase());
  }

  @Query(
      "select count(r) from LgpdDataSubjectRequest r where r.tenantId = :tenantId "
          + "and r.assignedToUserId is null and upper(r.status) <> 'ENCERRADO'")
  long countUnassignedActiveRaw(@Param("tenantId") UUID tenantId);

  /** Espelha {@code requestRepository.countUnassignedActive(tenantId)}. */
  default long countUnassignedActive(UUID tenantId) {
    return countUnassignedActiveRaw(tenantId);
  }

  @Query(
      "select count(r) from LgpdDataSubjectRequest r where r.tenantId = :tenantId "
          + "and r.createdAt < :cutoff and upper(r.status) in ('ABERTO', 'EM_VALIDACAO')")
  long countOverdueInitialResponseRaw(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

  /** Espelha {@code requestRepository.countOverdueInitialResponse(tenantId, cutoff)}. */
  default long countOverdueInitialResponse(UUID tenantId, Instant cutoff) {
    return countOverdueInitialResponseRaw(tenantId, cutoff);
  }

  @Query(
      "select count(r) from LgpdDataSubjectRequest r where r.tenantId = :tenantId "
          + "and r.createdAt < :cutoff and upper(r.status) <> 'ENCERRADO'")
  long countOverdueFinalResolutionRaw(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

  /** Espelha {@code requestRepository.countOverdueFinalResolution(tenantId, cutoff)}. */
  default long countOverdueFinalResolution(UUID tenantId, Instant cutoff) {
    return countOverdueFinalResolutionRaw(tenantId, cutoff);
  }

  @Query(
      "select r from LgpdDataSubjectRequest r where r.tenantId = :tenantId "
          + "and r.createdAt < :cutoff and upper(r.status) <> 'ENCERRADO' "
          + "order by r.createdAt asc, r.id asc")
  List<LgpdDataSubjectRequest> listOperationalAlertsRaw(
      @Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff, org.springframework.data.domain.Limit limit);

  /** Espelha {@code requestRepository.listOperationalAlerts(tenantId, cutoff, limit)}. */
  default List<LgpdDataSubjectRequest> listOperationalAlerts(UUID tenantId, Instant cutoff, Integer limit) {
    int normalizedLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
    return listOperationalAlertsRaw(tenantId, cutoff, org.springframework.data.domain.Limit.of(normalizedLimit));
  }
}
