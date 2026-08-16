package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

/**
 * Parte dinamica/nativa de {@code modules/scheduling/domain/repository/AgendamentoRepository.java}.
 *
 * <p>Separado da interface Spring Data {@link AgendamentoRepository} porque o {@code where} destas
 * consultas e montado em runtime a partir de filtros opcionais — mesmo desdobramento de
 * {@link TransacaoQueryRepository}.
 *
 * <p><b>Divergencia de mapeamento em relacao ao original</b>: as consultas de no-show do Quarkus
 * faziam {@code left join fetch a.client} e {@code left join fetch a.professional}. A entidade
 * {@link Agendamento} migrada nao tem essas associacoes (foram descartadas de proposito — o
 * service resolve cliente e profissional por repositorio, filtrando por tenant). O fetch join
 * restante ({@code items} + {@code item.service}) e o que evita o N+1 real; cliente e profissional
 * passam a ser resolvidos em lote pelo service, como ja acontece nos demais modulos migrados.
 */
@Repository
public class AgendamentoQueryRepository {

  private static final String NO_SHOW_DB_STATUS = "Nao compareceu";

  public record NoShowCursor(UUID id, LocalDate date, String startTime, Instant createdAt) {}

  @PersistenceContext private EntityManager entityManager;

  /**
   * Fix 3 (race condition de sobreposicao de agendamento): duas criacoes concorrentes para o mesmo
   * profissional/data (ex.: dois clientes do WhatsApp fechando horario ao mesmo tempo, ou webhook +
   * tela admin) rodam a leitura de conflitos antes de qualquer uma delas commitar o INSERT, entao
   * ambas veem "sem conflito" e ambas inserem — overlap silencioso em READ COMMITTED.
   *
   * <p>{@code pg_advisory_xact_lock} serializa as transacoes concorrentes na mesma chave
   * (tenant+profissional+data): a segunda transacao bloqueia ate a primeira commitar/abortar, e so
   * entao le os conflitos — ja enxergando o agendamento recem-criado. O lock e liberado
   * automaticamente no fim da transacao (commit ou rollback), sem unlock manual.
   *
   * <p>Optou-se por lock consultivo em vez de EXCLUDE CONSTRAINT porque o fluxo de negocio permite
   * criar um agendamento sobreposto de forma deliberada ({@code conflictOverride}) — uma constraint
   * de exclusao rejeitaria incondicionalmente qualquer overlap, quebrando esse fluxo. O advisory
   * lock apenas fecha a janela de corrida, sem alterar a regra de negocio existente.
   *
   * <p><b>Exige transacao ativa</b>: {@code pg_advisory_xact_lock} e liberado no fim da transacao,
   * entao chamar isto fora de uma transacao (Spring abriria e fecharia a conexao no proprio
   * {@code getSingleResult}) tornaria o lock inutil. Quem chama e {@code ServicoAgendamentos},
   * dentro do seu {@code @Transactional} de escrita.
   */
  public void lockProfessionalDateForWrite(UUID tenantId, UUID professionalId, LocalDate date) {
    if (tenantId == null || professionalId == null || date == null) return;
    String lockKey = tenantId + ":" + professionalId + ":" + date;
    entityManager
        .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:lockKey))")
        .setParameter("lockKey", lockKey)
        .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  public List<Object[]> contarPorDiaNoMes(UUID tenantId, int mes, int ano) {
    return entityManager
        .createNativeQuery(
            """
            SELECT
              EXTRACT(DAY FROM a.date)::int AS dia,
              EXTRACT(MONTH FROM a.date)::int AS mes,
              COUNT(*)::bigint AS quantidade
            FROM appointments a
            WHERE a.tenant_id = :tenantId
              AND EXTRACT(MONTH FROM a.date) = :mes
              AND EXTRACT(YEAR FROM a.date) = :ano
            GROUP BY dia, mes
            ORDER BY dia
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("mes", mes)
        .setParameter("ano", ano)
        .getResultList();
  }

  @SuppressWarnings("unchecked")
  public List<Object[]> contarPorDiaNoMesProfissional(
      UUID tenantId, UUID professionalId, int mes, int ano) {
    return entityManager
        .createNativeQuery(
            """
            SELECT
              EXTRACT(DAY FROM a.date)::int AS dia,
              EXTRACT(MONTH FROM a.date)::int AS mes,
              COUNT(*)::bigint AS quantidade
            FROM appointments a
            WHERE a.tenant_id = :tenantId
              AND a.professional_id = :professionalId
              AND EXTRACT(MONTH FROM a.date) = :mes
              AND EXTRACT(YEAR FROM a.date) = :ano
            GROUP BY dia, mes
            ORDER BY dia
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("professionalId", professionalId)
        .setParameter("mes", mes)
        .setParameter("ano", ano)
        .getResultList();
  }

  public long contarHistoricoClienteFiltrado(
      UUID tenantId, UUID clientId, LocalDate from, LocalDate to, UUID serviceId) {
    StringBuilder jpql =
        new StringBuilder(
            """
            select count(distinct a.id)
            from Agendamento a
            where a.tenantId = :tenantId
              and a.clientId = :clientId
            """);
    appendHistoryFilters(jpql, from, to, serviceId);

    TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
    applyHistoryParameters(query, tenantId, clientId, from, to, serviceId);
    return query.getSingleResult();
  }

  public List<Agendamento> listarHistoricoClienteFiltrado(
      UUID tenantId,
      UUID clientId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      int page,
      int size) {
    StringBuilder jpql =
        new StringBuilder(
            """
            select distinct a
            from Agendamento a
            where a.tenantId = :tenantId
              and a.clientId = :clientId
            """);
    appendHistoryFilters(jpql, from, to, serviceId);
    jpql.append(" order by a.date desc, a.startTime desc");

    TypedQuery<Agendamento> query = entityManager.createQuery(jpql.toString(), Agendamento.class);
    applyHistoryParameters(query, tenantId, clientId, from, to, serviceId);
    return query.setFirstResult(page * size).setMaxResults(size).getResultList();
  }

  private void appendHistoryFilters(StringBuilder jpql, LocalDate from, LocalDate to, UUID serviceId) {
    if (from != null) jpql.append(" and a.date >= :from");
    if (to != null) jpql.append(" and a.date <= :to");
    if (serviceId != null) {
      jpql.append(
          """
           and exists (
             select 1 from AgendamentoItem item
             where item.tenantId = :tenantId
               and item.appointmentId = a.id
               and item.serviceId = :serviceId
           )
          """);
    }
  }

  private void applyHistoryParameters(
      TypedQuery<?> query,
      UUID tenantId,
      UUID clientId,
      LocalDate from,
      LocalDate to,
      UUID serviceId) {
    query.setParameter("tenantId", tenantId);
    query.setParameter("clientId", clientId);
    if (from != null) query.setParameter("from", from);
    if (to != null) query.setParameter("to", to);
    if (serviceId != null) query.setParameter("serviceId", serviceId);
  }

  public Optional<NoShowCursor> findNoShowCursor(UUID tenantId, UUID appointmentId) {
    if (tenantId == null || appointmentId == null) return Optional.empty();
    Object[] row =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    select a.id, a.date, a.start_time, a.created_at
                    from appointments a
                    where a.tenant_id = :tenantId
                      and a.id = :appointmentId
                      and a.status = :noShowStatus
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("appointmentId", appointmentId)
                .setParameter("noShowStatus", NO_SHOW_DB_STATUS)
                .getResultStream()
                .findFirst()
                .orElse(null);
    if (row == null) return Optional.empty();
    return Optional.of(
        new NoShowCursor(
            toUuid(row[0]),
            toLocalDate(row[1]),
            row[2] != null ? row[2].toString() : null,
            toInstant(row[3])));
  }

  public long contarNoShowsFiltrados(
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            select count(*)
            from appointments a
            left join clients c
              on c.id = a.client_id
             and c.tenant_id = a.tenant_id
            where a.tenant_id = :tenantId
              and a.status = :noShowStatus
            """);
    applyNoShowFilters(sql, professionalId, from, to, serviceId, clientQuery);
    Number value =
        (Number)
            createNoShowBaseQuery(
                    sql.toString(), tenantId, professionalId, from, to, serviceId, clientQuery)
                .getSingleResult();
    return value != null ? value.longValue() : 0L;
  }

  public List<Agendamento> listarNoShowsFiltrados(
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery,
      UUID afterId,
      int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    NoShowCursor cursor =
        afterId == null
            ? null
            : findNoShowCursor(tenantId, afterId)
                .orElseThrow(() -> new IllegalArgumentException("Cursor de paginacao invalido"));

    StringBuilder sql =
        new StringBuilder(
            """
            select a.id
            from appointments a
            left join clients c
              on c.id = a.client_id
             and c.tenant_id = a.tenant_id
            where a.tenant_id = :tenantId
              and a.status = :noShowStatus
            """);
    applyNoShowFilters(sql, professionalId, from, to, serviceId, clientQuery);
    if (cursor != null) {
      sql.append(
          """

           and (
                a.date < :cursorDate
             or (a.date = :cursorDate and a.start_time < :cursorStartTime)
             or (a.date = :cursorDate and a.start_time = :cursorStartTime and a.created_at < :cursorCreatedAt)
             or (a.date = :cursorDate and a.start_time = :cursorStartTime and a.created_at = :cursorCreatedAt and a.id::text < :cursorIdText)
          )
          """);
    }
    sql.append(
        """

        order by a.date desc, a.start_time desc, a.created_at desc, a.id::text desc
        limit :limit
        """);

    Query idsQuery =
        createNoShowBaseQuery(
                sql.toString(), tenantId, professionalId, from, to, serviceId, clientQuery)
            .setParameter("limit", safeLimit + 1);
    if (cursor != null) {
      idsQuery.setParameter("cursorDate", cursor.date());
      idsQuery.setParameter("cursorStartTime", cursor.startTime());
      idsQuery.setParameter("cursorCreatedAt", cursor.createdAt());
      idsQuery.setParameter("cursorIdText", cursor.id() != null ? cursor.id().toString() : null);
    }

    @SuppressWarnings("unchecked")
    List<Object> idRows = idsQuery.getResultList();

    List<UUID> ids = idRows.stream().map(this::toUuid).toList();
    if (ids.isEmpty()) return List.of();
    List<UUID> pageIds = ids.size() > safeLimit ? ids.subList(0, safeLimit) : ids;

    return loadOrderedByIds(tenantId, pageIds);
  }

  public List<Agendamento> listarNoShowsFiltradosParaExportacao(
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            select a.id
            from appointments a
            left join clients c
              on c.id = a.client_id
             and c.tenant_id = a.tenant_id
            where a.tenant_id = :tenantId
              and a.status = :noShowStatus
            """);
    applyNoShowFilters(sql, professionalId, from, to, serviceId, clientQuery);
    sql.append(" order by a.date desc, a.start_time desc, a.created_at desc, a.id::text desc");

    @SuppressWarnings("unchecked")
    List<Object> idRows =
        createNoShowBaseQuery(
                sql.toString(), tenantId, professionalId, from, to, serviceId, clientQuery)
            .getResultList();

    List<UUID> ids = idRows.stream().map(this::toUuid).toList();
    if (ids.isEmpty()) return List.of();

    return loadOrderedByIds(tenantId, ids);
  }

  /**
   * Recarrega os agendamentos da pagina com os itens (e seus servicos) ja materializados e
   * reordena em memoria segundo a ordem dos ids — o SQL de cursor devolve so os ids, e um
   * {@code in (...)} nao preserva ordem. Identico ao original.
   *
   * <p>Materializar aqui, dentro da transacao do service, tambem e o que permite serializar o
   * resultado num export sem {@code LazyInitializationException}.
   */
  private List<Agendamento> loadOrderedByIds(UUID tenantId, List<UUID> ids) {
    List<Agendamento> loaded =
        entityManager
            .createQuery(
                """
                select distinct a
                from Agendamento a
                left join fetch a.items item
                left join fetch item.service
                where a.tenantId = :tenantId
                  and a.id in :ids
                """,
                Agendamento.class)
            .setParameter("tenantId", tenantId)
            .setParameter("ids", ids)
            .getResultList();

    List<Agendamento> ordered = new ArrayList<>();
    for (UUID id : ids) {
      loaded.stream()
          .filter(item -> id.equals(item.getId()))
          .findFirst()
          .ifPresent(ordered::add);
    }
    return ordered;
  }

  private Query createNoShowBaseQuery(
      String sql,
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery) {
    Query query =
        entityManager
            .createNativeQuery(sql)
            .setParameter("tenantId", tenantId)
            .setParameter("noShowStatus", NO_SHOW_DB_STATUS);
    if (professionalId != null) query.setParameter("professionalId", professionalId);
    if (from != null) query.setParameter("from", from);
    if (to != null) query.setParameter("to", to);
    if (serviceId != null) query.setParameter("serviceId", serviceId);
    if (clientQuery != null && !clientQuery.isBlank()) {
      query.setParameter("clientQuery", "%" + clientQuery.trim().toLowerCase() + "%");
    }
    return query;
  }

  private void applyNoShowFilters(
      StringBuilder sql,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery) {
    if (professionalId != null) {
      sql.append(" and a.professional_id = :professionalId");
    }
    if (from != null) {
      sql.append(" and a.date >= :from");
    }
    if (to != null) {
      sql.append(" and a.date <= :to");
    }
    if (serviceId != null) {
      sql.append(
          """

           and exists (
             select 1
             from appointment_items item
             where item.tenant_id = a.tenant_id
               and item.appointment_id = a.id
               and item.service_id = :serviceId
          )
          """);
    }
    if (clientQuery != null && !clientQuery.isBlank()) {
      sql.append(
          """

           and (
                lower(coalesce(c.name, '')) like :clientQuery
             or lower(coalesce(c.email, '')) like :clientQuery
             or lower(coalesce(c.phone, '')) like :clientQuery
           )
          """);
    }
  }

  private UUID toUuid(Object value) {
    if (value == null) return null;
    if (value instanceof UUID uuid) return uuid;
    return UUID.fromString(value.toString());
  }

  private LocalDate toLocalDate(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate date) return date;
    if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
    return LocalDate.parse(value.toString());
  }

  private Instant toInstant(Object value) {
    if (value == null) return null;
    if (value instanceof Instant instant) return instant;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof java.util.Date date) return date.toInstant();
    return DataUtil.parseInstantISO(value.toString());
  }
}
