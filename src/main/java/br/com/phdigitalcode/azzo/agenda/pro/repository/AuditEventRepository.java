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

  /** Os eventos da entidade que ainda guardam o conteudo original (para a redacao de LGPD). */
  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.entityType = :entityType "
          + "and e.entityId = :entityId and e.redactedAt is null")
  List<AuditEvent> findNaoRedigidosDaEntidade(
      @Param("tenantId") UUID tenantId,
      @Param("entityType") String entityType,
      @Param("entityId") String entityId);

  /**
   * Liga, SO nesta transacao, a excecao do gatilho de {@code audit_events} que permite a redacao.
   * Sem isto o UPDATE e recusado ("append-only table").
   */
  @Query(value = "select set_config('app.audit_redaction', 'on', true)", nativeQuery = true)
  String habilitarRedacaoNestaTransacao();

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

  // ---- cadeia selada (v2, V164) ----

  /** Eventos v2 ja confirmados e ainda sem selo, na ordem em que foram criados. */
  @Query(
      "select e from AuditEvent e where e.hashVersion = 2 and e.chainSeq is null "
          + "order by e.createdAt asc, e.id asc")
  List<AuditEvent> findNaoSelados(Limit limit);

  @Query("select e from AuditEvent e where e.tenantId = :tenantId and e.chainSeq is not null order by e.chainSeq desc")
  List<AuditEvent> findUltimosSelados(@Param("tenantId") UUID tenantId, Limit limit);

  default Optional<AuditEvent> ultimoSelado(UUID tenantId) {
    return findUltimosSelados(tenantId, Limit.of(1)).stream().findFirst();
  }

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.hashVersion = 1 "
          + "order by e.createdAt desc, e.id desc")
  List<AuditEvent> findUltimosLegados(@Param("tenantId") UUID tenantId, Limit limit);

  default Optional<AuditEvent> ultimoLegado(UUID tenantId) {
    return findUltimosLegados(tenantId, Limit.of(1)).stream().findFirst();
  }

  /** Um selador por vez, em qualquer quantidade de instancias da API: quem nao pega a trava pula a rodada. */
  @Query(value = "select pg_try_advisory_xact_lock(hashtext('audit_events:selo'))", nativeQuery = true)
  boolean travaDoSelador();

  /** A mesma trava, esperando: o expurgo nao pode mexer na cadeia enquanto o selador liga eventos. */
  @Query(
      value = "select 1 from (select pg_advisory_xact_lock(hashtext('audit_events:selo'))) t",
      nativeQuery = true)
  Integer esperarTravaDoSelador();

  /** Liga, SO nesta transacao, a excecao do gatilho que permite selar (V164). */
  @Query(value = "select set_config('app.audit_seal', 'on', true)", nativeQuery = true)
  String habilitarSeloNestaTransacao();

  @Query("select e from AuditEvent e where e.tenantId = :tenantId and e.chainSeq = :posicao")
  Optional<AuditEvent> naPosicao(@Param("tenantId") UUID tenantId, @Param("posicao") long posicao);

  // ---- conferencia (paginas por chave, sem OFFSET) ----

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.hashVersion = 1 "
          + "and (e.createdAt > :depoisDe or (e.createdAt = :depoisDe and e.id > :depoisDoId)) "
          + "order by e.createdAt asc, e.id asc")
  List<AuditEvent> paginaDoLegado(
      @Param("tenantId") UUID tenantId,
      @Param("depoisDe") Instant depoisDe,
      @Param("depoisDoId") UUID depoisDoId,
      Limit limit);

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.chainSeq > :depoisDe "
          + "order by e.chainSeq asc")
  List<AuditEvent> paginaDaCadeia(@Param("tenantId") UUID tenantId, @Param("depoisDe") long depoisDe, Limit limit);

  @Query(
      "select count(e) from AuditEvent e where e.tenantId = :tenantId and e.hashVersion = 2 "
          + "and e.chainSeq is null and e.createdAt < :antesDe")
  long contarSemSeloAntesDe(@Param("tenantId") UUID tenantId, @Param("antesDe") Instant antesDe);

  @Query(value = "SELECT DISTINCT tenant_id FROM audit_events", nativeQuery = true)
  List<UUID> tenantsComEventos();

  // ---- expurgo que respeita a cadeia ----

  /** A menor posicao selada que FICA (criada a partir do corte): o expurgo v2 remove so o que vem antes. */
  @Query(
      "select min(e.chainSeq) from AuditEvent e where e.tenantId = :tenantId and e.chainSeq is not null "
          + "and e.createdAt >= :cutoff")
  Long primeiraPosicaoQueFica(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.chainSeq is not null "
          + "and e.createdAt < :cutoff and e.chainSeq < :limite order by e.chainSeq desc")
  List<AuditEvent> ultimoSeladoAExpurgar(
      @Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff, @Param("limite") long limite, Limit limit);

  @Query(
      "select e from AuditEvent e where e.tenantId = :tenantId and e.hashVersion = 1 and e.createdAt < :cutoff "
          + "order by e.createdAt desc, e.id desc")
  List<AuditEvent> ultimoLegadoAExpurgar(
      @Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff, Limit limit);

  @Modifying
  @Query(
      value =
          """
          DELETE FROM audit_events
          WHERE tenant_id = :tenantId AND created_at < :cutoff
            AND (hash_version = 1 OR (chain_seq IS NOT NULL AND chain_seq <= :ultimaPosicao))
          """,
      nativeQuery = true)
  int deleteBeforeByTenantRespeitandoACadeia(
      @Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff, @Param("ultimaPosicao") long ultimaPosicao);

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

  /**
   * Liga, SO nesta transacao, a excecao do gatilho para o DELETE do expurgo. Era {@code @Modifying
   * void}: o driver recusava o SELECT ("um resultado foi retornado quando nenhum era esperado") e o
   * expurgo nunca rodou — achado no teste contra Postgres real em 07/11/2026, antes de algum evento
   * passar dos 365 dias. Sem {@code @Transactional} proprio: vale a transacao de quem chama.
   */
  @Query(value = "SELECT set_config('app.audit_retention_purge', 'on', true)", nativeQuery = true)
  String enableRetentionPurgeFlag();

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
            -- CAST obrigatorio: em `:from IS NULL` o Postgres nao tem como deduzir o tipo do
            -- parametro e recusa a consulta inteira ("could not determine data type of parameter"),
            -- com ou sem valor. A tela de auditoria ficava sem os filtros (achado de 2026-09-20).
            AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
            AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
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
