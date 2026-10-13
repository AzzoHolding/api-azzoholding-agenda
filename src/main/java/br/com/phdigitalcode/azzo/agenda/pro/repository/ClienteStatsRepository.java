package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Espelha a parte de estatisticas de {@code ClienteRepository} do Quarkus original (queries
 * nativas de visitas/gasto por cliente).
 *
 * <p>Separado do {@link ClienteRepository} (interface Spring Data) porque Spring Data JPA nao
 * permite metodos com {@code EntityManager} proprio dentro da interface sem um fragmento
 * customizado — este bean e o equivalente direto, injetado por construtor onde necessario.
 *
 * <p>As queries batem em {@code appointments}/{@code appointment_items}: tabelas que ja existem
 * no schema (criadas pelas migrations Flyway) mesmo com o modulo {@code scheduling} ainda nao
 * portado como entidade JPA — SQL nativo nao depende de mapeamento Java.
 */
@Repository
public class ClienteStatsRepository {

  public record ClienteStats(int totalVisits, BigDecimal totalSpent, LocalDate lastVisit) {
    public static final ClienteStats EMPTY = new ClienteStats(0, NumericUtil.zero(), null);
  }

  @PersistenceContext
  private EntityManager entityManager;

  public ClienteStats findStatsByTenantAndClient(UUID tenantId, UUID clientId) {
    if (tenantId == null || clientId == null) return ClienteStats.EMPTY;
    Object[] row = (Object[]) entityManager
        .createNativeQuery(
            // O que o cliente PAGOU (ReceitaDeClientesSql), e nao o preco dos agendamentos.
            "WITH " + br.com.phdigitalcode.azzo.agenda.pro.util.ReceitaDeClientesSql.CTE + """
            SELECT
              COUNT(DISTINCT v.visita_id)::int AS total_visits,
              COALESCE(SUM(v.valor), 0) AS total_spent,
              MAX(v.dia) AS last_visit
            FROM visitas_clientes v
            WHERE v.client_id = :clientId
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("clientId", clientId)
        .getSingleResult();
    return toStats(row);
  }

  @SuppressWarnings("unchecked")
  public Map<UUID, ClienteStats> findStatsByTenant(UUID tenantId) {
    Map<UUID, ClienteStats> stats = new HashMap<>();
    if (tenantId == null) return stats;

    List<Object[]> rows = entityManager
        .createNativeQuery(
            "WITH " + br.com.phdigitalcode.azzo.agenda.pro.util.ReceitaDeClientesSql.CTE + """
            SELECT
              v.client_id,
              COUNT(DISTINCT v.visita_id)::int AS total_visits,
              COALESCE(SUM(v.valor), 0) AS total_spent,
              MAX(v.dia) AS last_visit
            FROM visitas_clientes v
            GROUP BY v.client_id
            """)
        .setParameter("tenantId", tenantId)
        .getResultList();

    for (Object[] row : rows) {
      UUID clientId = row[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[0]));
      stats.put(clientId, toStats(new Object[] {row[1], row[2], row[3]}));
    }
    return stats;
  }

  @SuppressWarnings("unchecked")
  public Map<UUID, ClienteStats> findStatsByTenantAndClientIds(UUID tenantId, List<UUID> clientIds) {
    Map<UUID, ClienteStats> stats = new HashMap<>();
    if (tenantId == null || clientIds == null || clientIds.isEmpty()) return stats;

    List<UUID> filtered = clientIds.stream().filter(Objects::nonNull).distinct().toList();
    if (filtered.isEmpty()) return stats;

    StringBuilder placeholders = new StringBuilder();
    for (int i = 0; i < filtered.size(); i++) {
      if (i > 0) placeholders.append(", ");
      placeholders.append(":clientId").append(i);
    }

    String sql = ("WITH " + br.com.phdigitalcode.azzo.agenda.pro.util.ReceitaDeClientesSql.CTE + """
        SELECT
          v.client_id,
          COUNT(DISTINCT v.visita_id)::int AS total_visits,
          COALESCE(SUM(v.valor), 0) AS total_spent,
          MAX(v.dia) AS last_visit
        FROM visitas_clientes v
        WHERE v.client_id IN (%s)
        GROUP BY v.client_id
        """).formatted(placeholders);

    var query = entityManager.createNativeQuery(sql).setParameter("tenantId", tenantId);
    for (int i = 0; i < filtered.size(); i++) {
      query.setParameter("clientId" + i, filtered.get(i));
    }

    List<Object[]> rows = query.getResultList();
    for (Object[] row : rows) {
      UUID clientId = row[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[0]));
      stats.put(clientId, toStats(new Object[] {row[1], row[2], row[3]}));
    }
    return stats;
  }

  /** Materia-prima dos indicadores da ficha (ver {@code IndicadoresDoCliente}). */
  public record IndicadoresBrutos(
      List<LocalDate> diasDeVisita, int concluidos, int faltas, int cancelados, BigDecimal gastoDesde) {}

  @SuppressWarnings("unchecked")
  public IndicadoresBrutos findIndicadoresBrutos(UUID tenantId, UUID clientId, LocalDate desde, LocalDate ate) {
    String cte = "WITH " + br.com.phdigitalcode.azzo.agenda.pro.util.ReceitaDeClientesSql.CTE;
    List<Object> dias = entityManager
        .createNativeQuery(cte + """
            SELECT DISTINCT v.dia FROM visitas_clientes v
            WHERE v.client_id = :clientId AND v.dia <= :ate
            ORDER BY v.dia
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("clientId", clientId)
        .setParameter("ate", ate)
        .getResultList();
    Object gasto = entityManager
        .createNativeQuery(cte + """
            SELECT COALESCE(SUM(v.valor), 0) FROM visitas_clientes v
            WHERE v.client_id = :clientId AND v.dia BETWEEN :desde AND :ate
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("clientId", clientId)
        .setParameter("desde", desde)
        .setParameter("ate", ate)
        .getSingleResult();
    // A coluna `status` guarda a descricao em portugues (ver StatusAgendamentoConverter).
    Object[] contagem = (Object[]) entityManager
        .createNativeQuery("""
            SELECT
              COUNT(*) FILTER (WHERE a.status = 'Concluido')::int,
              COUNT(*) FILTER (WHERE a.status = 'Nao compareceu')::int,
              COUNT(*) FILTER (WHERE a.status = 'Cancelado')::int
            FROM appointments a
            WHERE a.tenant_id = :tenantId AND a.client_id = :clientId
              AND a.date BETWEEN :desde AND :ate
            """)
        .setParameter("tenantId", tenantId)
        .setParameter("clientId", clientId)
        .setParameter("desde", desde)
        .setParameter("ate", ate)
        .getSingleResult();
    return new IndicadoresBrutos(
        dias.stream().map(ClienteStatsRepository::paraData).toList(),
        contagem[0] != null ? ((Number) contagem[0]).intValue() : 0,
        contagem[1] != null ? ((Number) contagem[1]).intValue() : 0,
        contagem[2] != null ? ((Number) contagem[2]).intValue() : 0,
        NumericUtil.normalize(gasto instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(gasto))));
  }

  private static LocalDate paraData(Object valor) {
    if (valor instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate();
    if (valor instanceof LocalDate localDate) return localDate;
    return LocalDate.parse(String.valueOf(valor));
  }

  private ClienteStats toStats(Object[] row) {
    if (row == null || row.length < 3) return ClienteStats.EMPTY;
    int totalVisits = row[0] != null ? ((Number) row[0]).intValue() : 0;
    BigDecimal totalSpent =
        NumericUtil.normalize(row[1] instanceof BigDecimal bd ? bd : new BigDecimal(row[1].toString()));
    LocalDate lastVisit = null;
    if (row[2] instanceof java.sql.Date sqlDate) {
      lastVisit = sqlDate.toLocalDate();
    } else if (row[2] instanceof LocalDate localDate) {
      lastVisit = localDate;
    } else if (row[2] != null) {
      lastVisit = LocalDate.parse(String.valueOf(row[2]));
    }
    return new ClienteStats(totalVisits, totalSpent, lastVisit);
  }
}
