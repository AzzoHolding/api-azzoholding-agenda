package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.ClienteTopServiceDto;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardCustomerRankingResponse;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Porte verbatim de {@code modules/customers/domain/repository/ClienteAnalyticsRepository.java}.
 * Portado junto com o modulo {@code reports} porque {@code listCustomerRanking} alimenta
 * {@code GET /api/v1/dashboard/metrics/customers}; {@code listTopServicesByTenantAndClient} nao e
 * consumida por nenhum service ainda (no original, {@code ClienteService} tambem so a usa quando
 * o modulo reports esta disponivel — aqui fica pronta para o mesmo ponto de integracao futuro sem
 * reescrever esta classe de novo).
 */
@Repository
public class ClienteAnalyticsRepository {

  @PersistenceContext private EntityManager entityManager;

  @SuppressWarnings("unchecked")
  public List<ClienteTopServiceDto> listTopServicesByTenantAndClient(UUID tenantId, UUID clientId, int limit) {
    if (tenantId == null || clientId == null) return List.of();

    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                WITH service_totals AS (
                  SELECT
                    m.tenant_id,
                    m.client_id,
                    m.service_id,
                    COALESCE(SUM(m.completed_appointments), 0)::int AS completed_appointments,
                    COALESCE(SUM(m.completed_services), 0)::int AS completed_services,
                    COALESCE(SUM(m.revenue_total), 0) AS revenue_total,
                    MAX(m.last_appointment_date) AS last_appointment_date
                  FROM mv_customer_top_services_daily m
                  WHERE m.tenant_id = :tenantId
                    AND m.client_id = :clientId
                  GROUP BY m.tenant_id, m.client_id, m.service_id
                ),
                professional_rank AS (
                  SELECT
                    m.tenant_id,
                    m.client_id,
                    m.service_id,
                    m.professional_id,
                    COALESCE(SUM(m.completed_services), 0)::int AS professional_completed_services,
                    ROW_NUMBER() OVER (
                      PARTITION BY m.tenant_id, m.client_id, m.service_id
                      ORDER BY COALESCE(SUM(m.completed_services), 0) DESC, m.professional_id ASC
                    ) AS ranking_position
                  FROM mv_customer_top_services_daily m
                  WHERE m.tenant_id = :tenantId
                    AND m.client_id = :clientId
                    AND m.professional_id IS NOT NULL
                  GROUP BY m.tenant_id, m.client_id, m.service_id, m.professional_id
                )
                SELECT
                  st.service_id,
                  s.name,
                  pr.professional_id,
                  p.name,
                  st.completed_appointments,
                  st.completed_services,
                  st.revenue_total,
                  st.last_appointment_date
                FROM service_totals st
                JOIN services s
                  ON s.id = st.service_id
                 AND s.tenant_id = st.tenant_id
                LEFT JOIN professional_rank pr
                  ON pr.tenant_id = st.tenant_id
                 AND pr.client_id = st.client_id
                 AND pr.service_id = st.service_id
                 AND pr.ranking_position = 1
                LEFT JOIN professionals p
                  ON p.id = pr.professional_id
                 AND p.tenant_id = st.tenant_id
                ORDER BY st.completed_services DESC, st.completed_appointments DESC, st.last_appointment_date DESC, s.name ASC
                LIMIT :limit
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("clientId", clientId)
            .setParameter("limit", Math.max(limit, 1))
            .getResultList();

    return rows.stream().map(this::toTopServiceDto).toList();
  }

  @SuppressWarnings("unchecked")
  public List<DashboardCustomerRankingResponse.CustomerMetric> listCustomerRanking(
      UUID tenantId, LocalDate start, LocalDate end, int limit) {
    if (tenantId == null || start == null || end == null) return List.of();

    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  m.client_id,
                  c.name,
                  COALESCE(SUM(m.completed_appointments), 0)::int AS completed_appointments,
                  COALESCE(SUM(m.completed_services), 0)::int AS completed_services,
                  COALESCE(SUM(m.revenue_total), 0)::bigint AS revenue_total,
                  MAX(m.last_appointment_date) AS last_appointment_date
                FROM mv_customer_service_rank_daily m
                JOIN clients c
                  ON c.id = m.client_id
                 AND c.tenant_id = m.tenant_id
                WHERE m.tenant_id = :tenantId
                  AND m.metric_date BETWEEN :startDate AND :endDate
                GROUP BY m.client_id, c.name
                ORDER BY completed_services DESC, completed_appointments DESC, last_appointment_date DESC, c.name ASC
                LIMIT :limit
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("startDate", start)
            .setParameter("endDate", end)
            .setParameter("limit", Math.max(limit, 1))
            .getResultList();

    int[] rank = {1};
    return rows.stream().map(row -> toCustomerMetric(row, rank[0]++)).toList();
  }

  public Instant findLastRefreshAt(String... viewNames) {
    if (viewNames == null || viewNames.length == 0) return null;
    Object value =
        entityManager
            .createNativeQuery(
                """
                SELECT MAX(refreshed_at)
                FROM report_materialized_view_refresh_state
                WHERE view_name = ANY (:viewNames)
                """)
            .setParameter("viewNames", viewNames)
            .getSingleResult();
    if (value == null) return null;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof Instant instant) return instant;
    return DataUtil.parseInstantISO(String.valueOf(value));
  }

  private ClienteTopServiceDto toTopServiceDto(Object[] row) {
    ClienteTopServiceDto dto = new ClienteTopServiceDto();
    dto.serviceId = row[0] == null ? null : row[0].toString();
    dto.serviceName = row[1] == null ? null : row[1].toString();
    dto.topProfessionalId = row[2] == null ? null : row[2].toString();
    dto.topProfessionalName = row[3] == null ? null : row[3].toString();
    dto.completedAppointments = toInt(row[4]);
    dto.completedServices = toInt(row[5]);
    dto.revenueTotal = toMoney(row[6]);
    dto.lastAppointmentDate = toLocalDateString(row[7]);
    return dto;
  }

  private DashboardCustomerRankingResponse.CustomerMetric toCustomerMetric(Object[] row, int rank) {
    DashboardCustomerRankingResponse.CustomerMetric dto = new DashboardCustomerRankingResponse.CustomerMetric();
    dto.rank = rank;
    dto.clientId = row[0] == null ? null : row[0].toString();
    dto.clientName = row[1] == null ? null : row[1].toString();
    dto.completedAppointments = toInt(row[2]);
    dto.completedServices = toInt(row[3]);
    dto.revenueTotal = toMoney(row[4]);
    dto.lastAppointmentDate = toLocalDateString(row[5]);
    return dto;
  }

  private int toInt(Object value) {
    if (value == null) return 0;
    return ((Number) value).intValue();
  }

  private BigDecimal toMoney(Object value) {
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal bd) return NumericUtil.normalize(bd);
    return NumericUtil.normalize(new BigDecimal(value.toString()));
  }

  private String toLocalDateString(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate localDate) return localDate.toString();
    if (value instanceof java.sql.Date sqlDate) return sqlDate.toLocalDate().toString();
    return String.valueOf(value);
  }
}
