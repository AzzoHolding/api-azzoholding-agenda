package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.AuditDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.AuditEvent;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;

/**
 * Espelha {@code modules/audit/domain/repository/AuditEventRepository.java}. Cobre tanto a
 * leitura usada pela timeline do agendamento (ja portada em sessao anterior) quanto os metodos de
 * escrita/retencao/consulta que faltavam, agora que o modulo {@code audit} foi migrado por
 * completo.
 */
@Repository
public interface AuditEventRepository
    extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.entityType = :entityType "
          + "and e.entityId = :entityId order by e.createdAt desc, e.id desc")
  List<AuditEvent> findByTenantAndEntity(
      @Param("tenantId") UUID tenantId,
      @Param("entityType") String entityType,
      @Param("entityId") String entityId,
      Limit limit);

  /**
   * Guardas de entrada e normalizacao do original: argumento nulo/em branco devolve lista vazia sem
   * tocar no banco, {@code limit <= 0} vira 100 e o teto e 200.
   */
  default List<AuditEvent> listByTenantAndEntity(
      UUID tenantId, String entityType, String entityId, int limit) {
    if (tenantId == null
        || entityType == null
        || entityType.isBlank()
        || entityId == null
        || entityId.isBlank()) {
      return List.of();
    }
    int safeLimit = limit <= 0 ? 100 : Math.min(limit, 200);
    return findByTenantAndEntity(tenantId, entityType.trim(), entityId.trim(), Limit.of(safeLimit));
  }

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId order by e.createdAt desc, e.id desc")
  List<AuditEvent> findByTenantOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId, Limit limit);

  /** Espelha {@code auditEventRepository.findLastByTenant(tenantId)}. */
  default Optional<AuditEvent> findLastByTenant(UUID tenantId) {
    if (tenantId == null) return Optional.empty();
    List<AuditEvent> result = findByTenantOrderByCreatedAtDesc(tenantId, Limit.of(1));
    return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
  }

  @Query(
      value =
          """
          SELECT DISTINCT tenant_id
          FROM audit_events
          WHERE created_at < :cutoff
          ORDER BY tenant_id
          LIMIT :maxTenants
          """,
      nativeQuery = true)
  List<UUID> findTenantIdsBefore(@Param("cutoff") Instant cutoff, @Param("maxTenants") int maxTenants);

  /** Espelha {@code auditEventRepository.findTenantIdsWithEventsBefore(cutoff, maxTenants)}. */
  default List<UUID> findTenantIdsWithEventsBefore(Instant cutoff, int maxTenants) {
    if (cutoff == null) return List.of();
    int limit = maxTenants < 1 ? 1000 : maxTenants;
    return findTenantIdsBefore(cutoff, limit);
  }

  @Query(
      value =
          "SELECT MIN(created_at) FROM audit_events WHERE tenant_id = :tenantId AND created_at < :cutoff",
      nativeQuery = true)
  Instant findOldestCreatedAtBeforeRaw(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

  /** Espelha {@code auditEventRepository.findOldestCreatedAtBefore(tenantId, cutoff)}. */
  default Optional<Instant> findOldestCreatedAtBefore(UUID tenantId, Instant cutoff) {
    if (tenantId == null || cutoff == null) return Optional.empty();
    return Optional.ofNullable(findOldestCreatedAtBeforeRaw(tenantId, cutoff));
  }

  @Modifying
  @Transactional
  @Query(value = "SELECT set_config('app.audit_retention_purge', 'on', true)", nativeQuery = true)
  void enableRetentionPurgeFlag();

  @Modifying
  @Transactional
  @Query(
      value = "DELETE FROM audit_events WHERE tenant_id = :tenantId AND created_at < :cutoff",
      nativeQuery = true)
  int deleteBeforeByTenant(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

  /**
   * Espelha {@code auditEventRepository.purgeBeforeByTenant(tenantId, cutoff)}: liga a flag de
   * sessao {@code app.audit_retention_purge} (usada por triggers/politicas de auditoria que
   * distinguem expurgo administrado de exclusao arbitraria) antes do DELETE.
   */
  default long purgeBeforeByTenant(UUID tenantId, Instant cutoff) {
    if (tenantId == null || cutoff == null) return 0;
    enableRetentionPurgeFlag();
    return deleteBeforeByTenant(tenantId, cutoff);
  }

  @Query(
      value =
          """
          SELECT DISTINCT module, status, action, entity_type, source_channel
          FROM audit_events
          WHERE tenant_id = :tenantId
            AND module <> :systemModule
            AND actor_user_id IS NOT NULL
            AND (:from IS NULL OR created_at >= :from)
            AND (:to IS NULL OR created_at <= :to)
          ORDER BY module, status, action, entity_type, source_channel
          """,
      nativeQuery = true)
  List<Object[]> findFilterOptionRows(
      @Param("tenantId") UUID tenantId,
      @Param("systemModule") String systemModule,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /** Espelha {@code auditEventRepository.aggregateFilterOptions(tenantId, from, to)}. */
  default AuditDtos.AuditFilterOptionsResponse aggregateFilterOptions(UUID tenantId, Instant from, Instant to) {
    List<Object[]> rows =
        findFilterOptionRows(tenantId, AuditConstants.Module.SYSTEM, from, to);

    LinkedHashSet<String> modules = new LinkedHashSet<>();
    LinkedHashSet<String> statuses = new LinkedHashSet<>();
    LinkedHashSet<String> actions = new LinkedHashSet<>();
    LinkedHashSet<String> entityTypes = new LinkedHashSet<>();
    LinkedHashSet<String> sourceChannels = new LinkedHashSet<>();

    for (Object[] row : rows) {
      if (row[0] != null && !row[0].toString().isBlank()) modules.add(row[0].toString());
      if (row[1] != null && !row[1].toString().isBlank()) statuses.add(row[1].toString());
      if (row[2] != null && !row[2].toString().isBlank()) actions.add(row[2].toString());
      if (row[3] != null && !row[3].toString().isBlank()) entityTypes.add(row[3].toString());
      if (row[4] != null && !row[4].toString().isBlank()) sourceChannels.add(row[4].toString());
    }

    AuditDtos.AuditFilterOptionsResponse response = new AuditDtos.AuditFilterOptionsResponse();
    response.modules = new ArrayList<>(modules);
    response.statuses = new ArrayList<>(statuses);
    response.actions = new ArrayList<>(actions);
    response.entityTypes = new ArrayList<>(entityTypes);
    response.sourceChannels = new ArrayList<>(sourceChannels);
    return response;
  }
}
