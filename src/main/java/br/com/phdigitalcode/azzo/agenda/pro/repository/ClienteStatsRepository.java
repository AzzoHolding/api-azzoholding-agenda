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
            """
            WITH appointment_totals AS (
              SELECT
                a.id,
                a.date,
                COALESCE(SUM(ai.total_price), 0) AS total_price
              FROM appointments a
              LEFT JOIN appointment_items ai ON ai.appointment_id = a.id
              WHERE a.tenant_id = :tenantId
                AND a.client_id = :clientId
                AND a.status = 'Concluido'
              GROUP BY a.id, a.date
            )
            SELECT
              COUNT(*)::int AS total_visits,
              COALESCE(SUM(at.total_price), 0) AS total_spent,
              MAX(at.date) AS last_visit
            FROM appointment_totals at
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
            """
            WITH appointment_totals AS (
              SELECT
                a.id,
                a.client_id,
                a.date,
                COALESCE(SUM(ai.total_price), 0) AS total_price
              FROM appointments a
              LEFT JOIN appointment_items ai ON ai.appointment_id = a.id
              WHERE a.tenant_id = :tenantId
                AND a.status = 'Concluido'
              GROUP BY a.id, a.client_id, a.date
            )
            SELECT
              at.client_id,
              COUNT(*)::int AS total_visits,
              COALESCE(SUM(at.total_price), 0) AS total_spent,
              MAX(at.date) AS last_visit
            FROM appointment_totals at
            GROUP BY at.client_id
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

    String sql = """
        WITH appointment_totals AS (
          SELECT
            a.id,
            a.client_id,
            a.date,
            COALESCE(SUM(ai.total_price), 0) AS total_price
          FROM appointments a
          LEFT JOIN appointment_items ai ON ai.appointment_id = a.id
          WHERE a.tenant_id = :tenantId
            AND a.status = 'Concluido'
            AND a.client_id IN (%s)
          GROUP BY a.id, a.client_id, a.date
        )
        SELECT
          at.client_id,
          COUNT(*)::int AS total_visits,
          COALESCE(SUM(at.total_price), 0) AS total_spent,
          MAX(at.date) AS last_visit
        FROM appointment_totals at
        GROUP BY at.client_id
        """.formatted(placeholders);

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
