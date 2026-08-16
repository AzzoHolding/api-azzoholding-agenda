package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Porte completo de {@code modules/reports/domain/repository/RelatorioRepository.java}.
 *
 * <p>Escrito originalmente numa etapa anterior (scheduling) apenas com o subconjunto consumido por
 * {@code ServicoAgendamentos} (relatorio gerencial de agendamentos + resumo/serie/agrupamentos de
 * no-show + {@code findLastRefreshAt}). Esta classe completa o restante: metricas de dashboard,
 * no-show detalhado com cursor, reativacao WhatsApp (series/summary lidos de view materializada —
 * a fila operacional ao vivo depende do modulo {@code chat}, ainda nao portado, e continua fora),
 * metricas de dashboard por profissional, desempenho de servicos e receita diaria. Nada foi
 * reescrito: as consultas abaixo sao porte verbatim, incluindo os nomes das views materializadas.
 *
 * <p>Todas leem <b>views materializadas</b> ({@code mv_dashboard_metrics_daily},
 * {@code mv_no_show_appointments}, {@code mv_relatorio_agendamentos},
 * {@code mv_appointment_booking_abandon_daily_stage}, {@code mv_whatsapp_booking_reactivation_daily},
 * {@code mv_dashboard_metrics_professional_daily}, {@code mv_dashboard_service_metrics_daily},
 * {@code mv_revenue_daily}) atualizadas por um scheduler proprio do modulo {@code reports}
 * ({@code ReportMaterializedViewRefreshScheduler}, fora deste modulo, ainda nao migrado). Enquanto
 * ele nao existir, os relatorios refletem o ultimo refresh feito pela aplicacao Quarkus;
 * {@code findLastRefreshAt} e justamente o que expoe essa defasagem ao frontend — nao e bug do
 * modulo, e comportamento herdado, sinalizado e nao corrigido aqui.
 *
 * <p>{@code appointments.status} guarda a <b>descricao em portugues</b> ({@code 'Concluido'}) — o
 * SQL nativo de {@link #contarAgendamentosConcluidos} compara com a descricao, nao com o nome do
 * enum. Igual ao original.
 */
@Repository
public class RelatorioRepository {

  @PersistenceContext private EntityManager entityManager;

  public static class NoShowSummaryRow {
    public int totalNoShows;
    public int previousPeriodNoShows;
    public int lastSevenDaysNoShows;
    public BigDecimal revenueAtRisk;
    public int completedAppointments;
  }

  public static class NoShowDailySummaryRow {
    public LocalDate date;
    public int totalNoShows;
    public BigDecimal revenueAtRisk;
  }

  public static class NoShowGroupedSummaryRow {
    public String key;
    public String label;
    public int totalNoShows;
    public BigDecimal revenueAtRisk;
  }

  public static class NoShowItemRow {
    public UUID appointmentId;
    public UUID clientId;
    public String clientName;
    public String clientPhone;
    public String clientEmail;
    public UUID professionalId;
    public String professionalName;
    public LocalDate date;
    public String startTime;
    public String endTime;
    public String status;
    public String notes;
    public BigDecimal totalPrice;
    public int totalServices;
    public List<String> serviceNames = List.of();
    public Instant createdAt;
  }

  public static class AppointmentManagementSummaryRow {
    public int totalAppointments;
    public int totalConfirmed;
    public int totalPending;
    public int totalCancelled;
    public int totalNoShow;
    public int totalCompleted;
    public BigDecimal totalRevenue;
    public int totalGapOpportunities;
    public int totalUnconfirmed;
    public int totalAbandonmentSignalDays;
  }

  public static class AppointmentManagementItemRow {
    public UUID appointmentId;
    public LocalDate date;
    public String startTime;
    public String endTime;
    public UUID clientId;
    public String clientName;
    public UUID professionalId;
    public String professionalName;
    public String serviceLabel;
    public String status;
    public String origin;
    public BigDecimal totalPrice;
    public boolean flagHorarioVago;
    public boolean flagNaoConfirmado;
    public boolean flagAbandonoFluxo;
  }

  public static class DashboardMetricsRow {
    public BigDecimal todayRevenue;
    public BigDecimal monthlyRevenue;
    public BigDecimal previousTodayRevenue;
    public BigDecimal previousMonthlyRevenue;
    public int todayAppointments;
    public int previousTodayAppointments;
    public int pendingAppointments;
    public int completedToday;
    public int totalClients;
    public int previousTotalClients;
    public int notConcludedToday;
    public int stoppedAtServiceSelection;
    public int stoppedAtProfessionalSelection;
    public int stoppedAtTimeSelection;
    public int stoppedAtFinalReview;
    public int whatsAppOpenFlowsToday;
    public int whatsAppStoppedAtServiceSelection;
    public int whatsAppStoppedAtProfessionalSelection;
    public int whatsAppStoppedAtTimeSelection;
    public int whatsAppStoppedAtFinalReview;
  }

  public static class WeeklyRevenueRow {
    public LocalDate date;
    public BigDecimal revenue;
  }

  public static class WhatsAppReactivationPointRow {
    public LocalDate metricDate;
    public int abandonedCount;
    public int reactivatedCount;
    public int convertedCount;
  }

  public static class WhatsAppReactivationSummaryRow {
    public int totalAbandoned;
    public int totalReactivated;
    public int totalConverted;
    public int stoppedAtServiceSelection;
    public int stoppedAtProfessionalSelection;
    public int stoppedAtTimeSelection;
    public int stoppedAtFinalReview;
  }

  public static class DashboardProfessionalMetricsRow {
    public BigDecimal revenueTotal;
    public BigDecimal commissionTotal;
    public int completedServices;
    public int clientsServed;
  }

  public static class ServicePerformanceRow {
    public UUID serviceId;
    public String serviceName;
    public int totalAppointments;
    public int completedAppointments;
    public int canceledAppointments;
    public BigDecimal revenueTotal;
  }

  public record NoShowCursorRow(UUID appointmentId, LocalDate metricDate, String startTime, Instant createdAt) {}

  // ============================================================
  // Dashboard
  // ============================================================

  public DashboardMetricsRow obterMetricasDashboard(
      UUID tenantId,
      LocalDate monthStart,
      LocalDate today,
      LocalDate yesterday,
      LocalDate previousMonthStart,
      LocalDate previousMonthEnd) {
    Object[] row =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COALESCE(MAX(m.today_revenue) FILTER (WHERE m.metric_date = :today), 0) AS today_revenue,
                      COALESCE(SUM(m.today_revenue) FILTER (WHERE m.metric_date BETWEEN :monthStart AND :today), 0) AS monthly_revenue,
                      COALESCE((
                        SELECT MAX(pm.today_revenue)
                        FROM mv_dashboard_metrics_daily pm
                        WHERE pm.tenant_id = :tenantId
                          AND pm.metric_date = :yesterday
                      ), 0) AS previous_today_revenue,
                      COALESCE((
                        SELECT SUM(pm.today_revenue)
                        FROM mv_dashboard_metrics_daily pm
                        WHERE pm.tenant_id = :tenantId
                          AND pm.metric_date BETWEEN :previousMonthStart AND :previousMonthEnd
                      ), 0) AS previous_monthly_revenue,
                      COALESCE(MAX(m.today_appointments) FILTER (WHERE m.metric_date = :today), 0)::int AS today_appointments,
                      COALESCE((
                        SELECT MAX(pm.today_appointments)
                        FROM mv_dashboard_metrics_daily pm
                        WHERE pm.tenant_id = :tenantId
                          AND pm.metric_date = :yesterday
                      ), 0)::int AS previous_today_appointments,
                      COALESCE(MAX(m.pending_appointments) FILTER (WHERE m.metric_date = :today), 0)::int AS pending_appointments,
                      COALESCE(MAX(m.completed_today) FILTER (WHERE m.metric_date = :today), 0)::int AS completed_today,
                      COALESCE(MAX(m.total_clients), 0)::int AS total_clients,
                      COALESCE((
                        SELECT MAX(pm.total_clients)
                        FROM mv_dashboard_metrics_daily pm
                        WHERE pm.tenant_id = :tenantId
                          AND pm.metric_date = :yesterday
                      ), 0)::int AS previous_total_clients,
                      COALESCE((
                        SELECT SUM(b.sessions_count)
                        FROM mv_appointment_booking_abandon_daily_stage b
                        WHERE b.tenant_id = :tenantId
                          AND b.metric_date = :today
                      ), 0)::int AS not_concluded_today,
                      COALESCE((
                        SELECT SUM(b.sessions_count)
                        FROM mv_appointment_booking_abandon_daily_stage b
                        WHERE b.tenant_id = :tenantId
                          AND b.metric_date = :today
                          AND b.stage = 'SERVICE_SELECTION'
                      ), 0)::int AS stopped_service_selection,
                      COALESCE((
                        SELECT SUM(b.sessions_count)
                        FROM mv_appointment_booking_abandon_daily_stage b
                        WHERE b.tenant_id = :tenantId
                          AND b.metric_date = :today
                          AND b.stage = 'PROFESSIONAL_SELECTION'
                      ), 0)::int AS stopped_professional_selection,
                      COALESCE((
                        SELECT SUM(b.sessions_count)
                        FROM mv_appointment_booking_abandon_daily_stage b
                        WHERE b.tenant_id = :tenantId
                          AND b.metric_date = :today
                          AND b.stage = 'TIME_SELECTION'
                      ), 0)::int AS stopped_time_selection,
                      COALESCE((
                        SELECT SUM(b.sessions_count)
                        FROM mv_appointment_booking_abandon_daily_stage b
                        WHERE b.tenant_id = :tenantId
                          AND b.metric_date = :today
                          AND b.stage = 'FINAL_REVIEW'
                      ), 0)::int AS stopped_final_review
                      ,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM whatsapp_booking_reactivation_cycles c
                        WHERE c.tenant_id = :tenantId
                          AND c.status = 'ACTIVE'
                          AND (c.updated_at AT TIME ZONE 'America/Sao_Paulo')::date = :today
                      ), 0)::int AS whatsapp_open_flows_today,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM whatsapp_booking_reactivation_cycles c
                        WHERE c.tenant_id = :tenantId
                          AND c.status = 'ACTIVE'
                          AND (c.updated_at AT TIME ZONE 'America/Sao_Paulo')::date = :today
                          AND c.last_stage = 'SERVICE_SELECTION'
                      ), 0)::int AS whatsapp_stopped_service_selection,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM whatsapp_booking_reactivation_cycles c
                        WHERE c.tenant_id = :tenantId
                          AND c.status = 'ACTIVE'
                          AND (c.updated_at AT TIME ZONE 'America/Sao_Paulo')::date = :today
                          AND c.last_stage = 'PROFESSIONAL_SELECTION'
                      ), 0)::int AS whatsapp_stopped_professional_selection,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM whatsapp_booking_reactivation_cycles c
                        WHERE c.tenant_id = :tenantId
                          AND c.status = 'ACTIVE'
                          AND (c.updated_at AT TIME ZONE 'America/Sao_Paulo')::date = :today
                          AND c.last_stage = 'TIME_SELECTION'
                      ), 0)::int AS whatsapp_stopped_time_selection,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM whatsapp_booking_reactivation_cycles c
                        WHERE c.tenant_id = :tenantId
                          AND c.status = 'ACTIVE'
                          AND (c.updated_at AT TIME ZONE 'America/Sao_Paulo')::date = :today
                          AND c.last_stage = 'FINAL_REVIEW'
                      ), 0)::int AS whatsapp_stopped_final_review
                    FROM mv_dashboard_metrics_daily m
                    WHERE m.tenant_id = :tenantId
                      AND m.metric_date BETWEEN :monthStart AND :today
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("monthStart", monthStart)
                .setParameter("today", today)
                .setParameter("yesterday", yesterday)
                .setParameter("previousMonthStart", previousMonthStart)
                .setParameter("previousMonthEnd", previousMonthEnd)
                .getSingleResult();

    DashboardMetricsRow response = new DashboardMetricsRow();
    response.todayRevenue = toMoney(row[0]);
    response.monthlyRevenue = toMoney(row[1]);
    response.previousTodayRevenue = toMoney(row[2]);
    response.previousMonthlyRevenue = toMoney(row[3]);
    response.todayAppointments = toInt(row[4]);
    response.previousTodayAppointments = toInt(row[5]);
    response.pendingAppointments = toInt(row[6]);
    response.completedToday = toInt(row[7]);
    response.totalClients = toInt(row[8]);
    response.previousTotalClients = toInt(row[9]);
    response.notConcludedToday = toInt(row[10]);
    response.stoppedAtServiceSelection = toInt(row[11]);
    response.stoppedAtProfessionalSelection = toInt(row[12]);
    response.stoppedAtTimeSelection = toInt(row[13]);
    response.stoppedAtFinalReview = toInt(row[14]);
    response.whatsAppOpenFlowsToday = toInt(row[15]);
    response.whatsAppStoppedAtServiceSelection = toInt(row[16]);
    response.whatsAppStoppedAtProfessionalSelection = toInt(row[17]);
    response.whatsAppStoppedAtTimeSelection = toInt(row[18]);
    response.whatsAppStoppedAtFinalReview = toInt(row[19]);
    return response;
  }

  public NoShowSummaryRow obterResumoNoShow(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      LocalDate previousStart,
      LocalDate previousEnd,
      LocalDate lastSevenDaysStart) {
    Object[] row =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COALESCE((
                        SELECT COUNT(*)
                        FROM mv_no_show_appointments n
                        WHERE n.tenant_id = :tenantId
                          AND n.metric_date BETWEEN :startDate AND :endDate
                      ), 0)::int AS total_no_shows,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM mv_no_show_appointments n
                        WHERE n.tenant_id = :tenantId
                          AND n.metric_date BETWEEN :previousStart AND :previousEnd
                      ), 0)::int AS previous_no_shows,
                      COALESCE((
                        SELECT COUNT(*)
                        FROM mv_no_show_appointments n
                        WHERE n.tenant_id = :tenantId
                          AND n.metric_date BETWEEN :lastSevenDaysStart AND :endDate
                      ), 0)::int AS last_seven_days,
                      COALESCE((
                        SELECT SUM(n.total_price)
                        FROM mv_no_show_appointments n
                        WHERE n.tenant_id = :tenantId
                          AND n.metric_date BETWEEN :startDate AND :endDate
                      ), 0) AS revenue_at_risk,
                      COALESCE((
                        SELECT SUM(m.completed_today)
                        FROM mv_dashboard_metrics_daily m
                        WHERE m.tenant_id = :tenantId
                          AND m.metric_date BETWEEN :startDate AND :endDate
                      ), 0)::int AS completed_appointments
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .setParameter("previousStart", previousStart)
                .setParameter("previousEnd", previousEnd)
                .setParameter("lastSevenDaysStart", lastSevenDaysStart)
                .getSingleResult();

    NoShowSummaryRow response = new NoShowSummaryRow();
    response.totalNoShows = toInt(row[0]);
    response.previousPeriodNoShows = toInt(row[1]);
    response.lastSevenDaysNoShows = toInt(row[2]);
    response.revenueAtRisk = toMoney(row[3]);
    response.completedAppointments = toInt(row[4]);
    return response;
  }

  public DashboardProfessionalMetricsRow obterMetricasDashboardProfissional(
      UUID tenantId, UUID professionalId, LocalDate start, LocalDate end) {
    String sql =
        """
        SELECT
          COALESCE(SUM(m.revenue), 0) AS revenue_total,
          COALESCE(SUM(m.commission), 0) AS commission_total,
          COALESCE(SUM(m.completed_services), 0)::int AS completed_services,
          COALESCE(SUM(m.clients_served), 0)::int AS clients_served
        FROM mv_dashboard_metrics_professional_daily m
        WHERE m.tenant_id = :tenantId
          AND m.metric_date BETWEEN :startDate AND :endDate
        """;
    if (professionalId != null) {
      sql += "\n  AND m.professional_id = :professionalId";
    }

    Query query =
        entityManager
            .createNativeQuery(sql)
            .setParameter("tenantId", tenantId)
            .setParameter("startDate", start)
            .setParameter("endDate", end);
    if (professionalId != null) {
      query.setParameter("professionalId", professionalId);
    }

    Object[] row = (Object[]) query.getSingleResult();

    DashboardProfessionalMetricsRow response = new DashboardProfessionalMetricsRow();
    response.revenueTotal = toMoney(row[0]);
    response.commissionTotal = toMoney(row[1]);
    response.completedServices = toInt(row[2]);
    response.clientsServed = toInt(row[3]);
    return response;
  }

  @SuppressWarnings("unchecked")
  public List<ServicePerformanceRow> listarDesempenhoServicos(
      UUID tenantId, UUID professionalId, LocalDate start, LocalDate end) {
    String sql =
        """
        SELECT
          m.service_id,
          s.name,
          COALESCE(SUM(m.total_appointments), 0)::int AS total_appointments,
          COALESCE(SUM(m.completed_appointments), 0)::int AS completed_appointments,
          COALESCE(SUM(m.canceled_appointments), 0)::int AS canceled_appointments,
          COALESCE(SUM(m.revenue), 0) AS revenue_total
        FROM mv_dashboard_service_metrics_daily m
        JOIN services s
          ON s.id = m.service_id
         AND s.tenant_id = m.tenant_id
        WHERE m.tenant_id = :tenantId
          AND m.metric_date BETWEEN :startDate AND :endDate
        """;
    if (professionalId != null) {
      sql += "\n  AND m.professional_id = :professionalId";
    }
    sql +=
        """
        GROUP BY m.service_id, s.name
        ORDER BY total_appointments DESC, s.name ASC
        """;

    Query query =
        entityManager
            .createNativeQuery(sql)
            .setParameter("tenantId", tenantId)
            .setParameter("startDate", start)
            .setParameter("endDate", end);
    if (professionalId != null) {
      query.setParameter("professionalId", professionalId);
    }

    List<Object[]> rows = query.getResultList();
    return rows.stream().map(this::toServicePerformanceRow).toList();
  }

  @SuppressWarnings("unchecked")
  public List<WeeklyRevenueRow> listarReceitaDiaria(UUID tenantId, LocalDate start, LocalDate end) {
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  s.metric_date::date AS metric_date,
                  COALESCE(r.revenue, 0) AS revenue
                FROM generate_series(CAST(:startDate AS date), CAST(:endDate AS date), interval '1 day') s(metric_date)
                LEFT JOIN mv_revenue_daily r
                  ON r.tenant_id = :tenantId
                 AND r.metric_date = s.metric_date::date
                ORDER BY s.metric_date
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("startDate", start)
            .setParameter("endDate", end)
            .getResultList();

    return rows.stream().map(this::toWeeklyRevenueRow).toList();
  }

  // ============================================================
  // Reativacao WhatsApp (series/summary via view materializada)
  // ============================================================

  @SuppressWarnings("unchecked")
  public List<WhatsAppReactivationPointRow> listarSerieReativacaoWhatsApp(
      UUID tenantId, LocalDate start, LocalDate end) {
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  d.metric_date::date AS metric_date,
                  COALESCE(m.abandoned_count, 0)::int AS abandoned_count,
                  COALESCE(m.reactivated_count, 0)::int AS reactivated_count,
                  COALESCE(m.converted_count, 0)::int AS converted_count
                FROM generate_series(CAST(:startDate AS date), CAST(:endDate AS date), interval '1 day') d(metric_date)
                LEFT JOIN mv_whatsapp_booking_reactivation_daily m
                  ON m.tenant_id = :tenantId
                 AND m.metric_date = d.metric_date::date
                ORDER BY d.metric_date
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("startDate", start)
            .setParameter("endDate", end)
            .getResultList();

    return rows.stream().map(this::toWhatsAppReactivationPointRow).toList();
  }

  public WhatsAppReactivationSummaryRow obterResumoReativacaoWhatsApp(
      UUID tenantId, LocalDate start, LocalDate end) {
    Object[] row =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COALESCE(SUM(abandoned_count), 0)::int AS total_abandoned,
                      COALESCE(SUM(reactivated_count), 0)::int AS total_reactivated,
                      COALESCE(SUM(converted_count), 0)::int AS total_converted,
                      COALESCE(SUM(stopped_at_service_selection), 0)::int AS stopped_service_selection,
                      COALESCE(SUM(stopped_at_professional_selection), 0)::int AS stopped_professional_selection,
                      COALESCE(SUM(stopped_at_time_selection), 0)::int AS stopped_time_selection,
                      COALESCE(SUM(stopped_at_final_review), 0)::int AS stopped_final_review
                    FROM mv_whatsapp_booking_reactivation_daily
                    WHERE tenant_id = :tenantId
                      AND metric_date BETWEEN :startDate AND :endDate
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .getSingleResult();

    WhatsAppReactivationSummaryRow response = new WhatsAppReactivationSummaryRow();
    response.totalAbandoned = toInt(row[0]);
    response.totalReactivated = toInt(row[1]);
    response.totalConverted = toInt(row[2]);
    response.stoppedAtServiceSelection = toInt(row[3]);
    response.stoppedAtProfessionalSelection = toInt(row[4]);
    response.stoppedAtTimeSelection = toInt(row[5]);
    response.stoppedAtFinalReview = toInt(row[6]);
    return response;
  }

  // ============================================================
  // Relatorio gerencial de agendamentos
  // ============================================================

  public AppointmentManagementSummaryRow obterResumoRelatorioAgendamentos(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      String status) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              COALESCE(SUM(m.total_agendamentos), 0)::int AS total_agendamentos,
              COALESCE(SUM(m.total_confirmados), 0)::int AS total_confirmados,
              COALESCE(SUM(m.total_pendentes), 0)::int AS total_pendentes,
              COALESCE(SUM(m.total_cancelados), 0)::int AS total_cancelados,
              COALESCE(SUM(m.total_no_show), 0)::int AS total_no_show,
              COALESCE(COUNT(*) FILTER (WHERE m.status = 'Concluido'), 0)::int AS total_concluidos,
              COALESCE(SUM(CASE WHEN m.status IN ('Concluido', 'Em andamento') THEN m.valor ELSE 0 END), 0) AS receita_total,
              COALESCE(SUM(CASE WHEN m.flag_horario_vago THEN 1 ELSE 0 END), 0)::int AS total_horario_vago,
              COALESCE(SUM(CASE WHEN m.flag_nao_confirmado THEN 1 ELSE 0 END), 0)::int AS total_nao_confirmado,
              COALESCE(COUNT(DISTINCT CASE WHEN m.flag_abandono_fluxo THEN m.data_agendamento END), 0)::int AS dias_abandono
            FROM mv_relatorio_agendamentos m
            WHERE m.tenant_id = :tenantId
            """);
    applyAppointmentManagementFilters(sql, professionalId, start, end, serviceId, status);
    Object[] row =
        (Object[])
            createAppointmentManagementQuery(
                    sql.toString(), tenantId, professionalId, start, end, serviceId, status)
                .getSingleResult();
    return toAppointmentManagementSummaryRow(row);
  }

  @SuppressWarnings("unchecked")
  public List<AppointmentManagementItemRow> listarRelatorioAgendamentos(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      String status,
      int limit) {
    StringBuilder sql = new StringBuilder(APPOINTMENT_MANAGEMENT_ITEM_SELECT);
    applyAppointmentManagementFilters(sql, professionalId, start, end, serviceId, status);
    sql.append(
        """

        ORDER BY m.data_agendamento DESC, m.horario ASC, m.appointment_id::text DESC
        LIMIT :limit
        """);
    List<Object[]> rows =
        createAppointmentManagementQuery(
                sql.toString(), tenantId, professionalId, start, end, serviceId, status)
            .setParameter("limit", Math.min(Math.max(limit, 1), 200))
            .getResultList();
    return rows.stream().map(this::toAppointmentManagementItemRow).toList();
  }

  @SuppressWarnings("unchecked")
  public List<AppointmentManagementItemRow> listarRelatorioAgendamentosPaginado(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      String status,
      int page,
      int pageSize) {
    int safePageSize = Math.min(Math.max(pageSize, 1), 100);
    int safePage = Math.max(page, 0);
    StringBuilder sql = new StringBuilder(APPOINTMENT_MANAGEMENT_ITEM_SELECT);
    applyAppointmentManagementFilters(sql, professionalId, start, end, serviceId, status);
    sql.append(
        """

        ORDER BY m.data_agendamento DESC, m.horario ASC, m.appointment_id::text DESC
        LIMIT :limit OFFSET :offset
        """);
    List<Object[]> rows =
        createAppointmentManagementQuery(
                sql.toString(), tenantId, professionalId, start, end, serviceId, status)
            .setParameter("limit", safePageSize + 1)
            .setParameter("offset", safePage * safePageSize)
            .getResultList();
    return rows.stream().map(this::toAppointmentManagementItemRow).toList();
  }

  /**
   * Projecao compartilhada por {@link #listarRelatorioAgendamentos} e
   * {@link #listarRelatorioAgendamentosPaginado} — no original as duas repetem o mesmo bloco
   * literal; aqui e uma constante para garantir que a ordem das colunas nunca divirja entre elas
   * (o mapeamento por indice em {@code toAppointmentManagementItemRow} depende disso).
   */
  private static final String APPOINTMENT_MANAGEMENT_ITEM_SELECT =
      """
      SELECT
        m.appointment_id,
        m.data_agendamento,
        m.horario,
        m.end_time,
        m.client_id,
        m.cliente,
        m.professional_id,
        m.profissional,
        m.servico,
        m.status,
        m.origem,
        m.valor,
        m.flag_horario_vago,
        m.flag_nao_confirmado,
        m.flag_abandono_fluxo
      FROM mv_relatorio_agendamentos m
      WHERE m.tenant_id = :tenantId
      """;

  // ============================================================
  // Relatorio de no-show
  // ============================================================

  public NoShowSummaryRow obterResumoNoShowFiltrado(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      LocalDate previousStart,
      LocalDate previousEnd,
      LocalDate lastSevenDaysStart,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    NoShowSummaryRow response = new NoShowSummaryRow();
    response.totalNoShows =
        (int) contarNoShowsNoPeriodo(tenantId, start, end, professionalId, serviceId, clientIds, clientQuery);
    response.previousPeriodNoShows =
        (int)
            contarNoShowsNoPeriodo(
                tenantId, previousStart, previousEnd, professionalId, serviceId, clientIds, clientQuery);
    response.lastSevenDaysNoShows =
        (int)
            contarNoShowsNoPeriodo(
                tenantId, lastSevenDaysStart, end, professionalId, serviceId, clientIds, clientQuery);
    response.revenueAtRisk =
        somarReceitaEmRiscoNoPeriodo(
            tenantId, start, end, professionalId, serviceId, clientIds, clientQuery);
    response.completedAppointments =
        contarAgendamentosConcluidos(
            tenantId, start, end, professionalId, serviceId, clientIds, clientQuery);
    return response;
  }

  @SuppressWarnings("unchecked")
  public List<NoShowDailySummaryRow> listarSerieNoShowDiaria(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              s.metric_date::date AS metric_date,
              COALESCE(n.total_no_shows, 0)::int AS total_no_shows,
              COALESCE(n.revenue_at_risk, 0) AS revenue_at_risk
            FROM generate_series(CAST(:startDate AS date), CAST(:endDate AS date), interval '1 day') s(metric_date)
            LEFT JOIN (
              SELECT
                n.metric_date,
                COUNT(*)::int AS total_no_shows,
                COALESCE(SUM(n.total_price), 0) AS revenue_at_risk
              FROM mv_no_show_appointments n
              WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, start, end, serviceId, clientIds, clientQuery);
    sql.append(
        """
              GROUP BY n.metric_date
            ) n
              ON n.metric_date = s.metric_date::date
            ORDER BY s.metric_date
            """);

    List<Object[]> rows =
        createNoShowDetailQuery(
                sql.toString(), tenantId, professionalId, start, end, serviceId, clientIds, clientQuery)
            .setParameter("startDate", start)
            .setParameter("endDate", end)
            .getResultList();
    return rows.stream().map(this::toNoShowDailySummaryRow).toList();
  }

  @SuppressWarnings("unchecked")
  public List<NoShowGroupedSummaryRow> listarResumoNoShowAgrupadoPorProfissional(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              COALESCE(n.professional_id::text, 'UNASSIGNED') AS group_key,
              COALESCE(n.professional_name, 'Sem profissional') AS group_label,
              COUNT(*)::int AS total_no_shows,
              COALESCE(SUM(n.total_price), 0) AS revenue_at_risk
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, start, end, serviceId, clientIds, clientQuery);
    sql.append(
        """

        GROUP BY COALESCE(n.professional_id::text, 'UNASSIGNED'), COALESCE(n.professional_name, 'Sem profissional')
        ORDER BY total_no_shows DESC, group_label ASC
        """);
    List<Object[]> rows =
        createNoShowDetailQuery(
                sql.toString(), tenantId, professionalId, start, end, serviceId, clientIds, clientQuery)
            .getResultList();
    return rows.stream().map(this::toNoShowGroupedSummaryRow).toList();
  }

  @SuppressWarnings("unchecked")
  public List<NoShowGroupedSummaryRow> listarResumoNoShowAgrupadoPorCliente(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              COALESCE(n.client_id::text, 'UNASSIGNED') AS group_key,
              COALESCE(n.client_name, 'Sem cliente') AS group_label,
              COUNT(*)::int AS total_no_shows,
              COALESCE(SUM(n.total_price), 0) AS revenue_at_risk
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, start, end, serviceId, clientIds, clientQuery);
    sql.append(
        """

        GROUP BY COALESCE(n.client_id::text, 'UNASSIGNED'), COALESCE(n.client_name, 'Sem cliente')
        ORDER BY total_no_shows DESC, group_label ASC
        """);
    List<Object[]> rows =
        createNoShowDetailQuery(
                sql.toString(), tenantId, professionalId, start, end, serviceId, clientIds, clientQuery)
            .getResultList();
    return rows.stream().map(this::toNoShowGroupedSummaryRow).toList();
  }

  @SuppressWarnings("unchecked")
  public List<NoShowGroupedSummaryRow> listarResumoNoShowAgrupadoPorServico(
      UUID tenantId,
      LocalDate start,
      LocalDate end,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              service_data.service_id AS group_key,
              service_data.service_name AS group_label,
              COUNT(DISTINCT n.appointment_id)::int AS total_no_shows,
              COALESCE(SUM(service_data.service_price), 0) AS revenue_at_risk
            FROM mv_no_show_appointments n
            JOIN LATERAL (
              SELECT
                s.id::text AS service_id,
                s.name AS service_name,
                COALESCE(SUM(ai.total_price), 0) AS service_price
              FROM appointment_items ai
              JOIN services s
                ON s.id = ai.service_id
               AND s.tenant_id = ai.tenant_id
              WHERE ai.appointment_id = n.appointment_id
                AND ai.tenant_id = n.tenant_id
              GROUP BY s.id, s.name
            ) service_data ON TRUE
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, start, end, serviceId, clientIds, clientQuery);
    sql.append(
        """

        GROUP BY service_data.service_id, service_data.service_name
        ORDER BY total_no_shows DESC, group_label ASC
        """);
    List<Object[]> rows =
        createNoShowDetailQuery(
                sql.toString(), tenantId, professionalId, start, end, serviceId, clientIds, clientQuery)
            .getResultList();
    return rows.stream().map(this::toNoShowGroupedSummaryRow).toList();
  }

  @SuppressWarnings("unchecked")
  public List<NoShowItemRow> listarNoShowsRecentes(UUID tenantId, LocalDate start, LocalDate end, int limit) {
    List<Object[]> rows =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  appointment_id,
                  client_id,
                  client_name,
                  client_phone,
                  client_email,
                  professional_id,
                  professional_name,
                  metric_date,
                  start_time,
                  end_time,
                  status,
                  notes,
                  total_price,
                  total_services,
                  service_ids,
                  service_names,
                  created_at
                FROM mv_no_show_appointments
                WHERE tenant_id = :tenantId
                  AND metric_date BETWEEN :startDate AND :endDate
                ORDER BY metric_date DESC, start_time DESC, created_at DESC, appointment_id::text DESC
                LIMIT :limit
                """)
            .setParameter("tenantId", tenantId)
            .setParameter("startDate", start)
            .setParameter("endDate", end)
            .setParameter("limit", Math.min(Math.max(limit, 1), 20))
            .getResultList();
    return rows.stream().map(this::toNoShowItemRow).toList();
  }

  public long contarNoShowsDetalhados(
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(*)
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, from, to, serviceId, List.of(), clientQuery);
    Number value =
        (Number)
            createNoShowDetailQuery(
                    sql.toString(), tenantId, professionalId, from, to, serviceId, List.of(), clientQuery)
                .getSingleResult();
    return value != null ? value.longValue() : 0L;
  }

  public List<NoShowItemRow> listarNoShowsDetalhados(
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery,
      UUID afterId,
      int limit) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    NoShowCursorRow cursor =
        afterId == null
            ? null
            : findNoShowCursor(tenantId, afterId)
                .orElseThrow(() -> new IllegalArgumentException("Cursor de paginacao invalido"));

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              appointment_id,
              client_id,
              client_name,
              client_phone,
              client_email,
              professional_id,
              professional_name,
              metric_date,
              start_time,
              end_time,
              status,
              notes,
              total_price,
              total_services,
              service_ids,
              service_names,
              created_at
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, from, to, serviceId, List.of(), clientQuery);
    if (cursor != null) {
      sql.append(
          """

               AND (
                    n.metric_date < :cursorDate
                 OR (n.metric_date = :cursorDate AND n.start_time < :cursorStartTime)
                 OR (n.metric_date = :cursorDate AND n.start_time = :cursorStartTime AND n.created_at < :cursorCreatedAt)
                 OR (n.metric_date = :cursorDate AND n.start_time = :cursorStartTime AND n.created_at = :cursorCreatedAt AND n.appointment_id::text < :cursorIdText)
               )
              """);
    }
    sql.append(
        """

        ORDER BY n.metric_date DESC, n.start_time DESC, n.created_at DESC, n.appointment_id::text DESC
        LIMIT :limit
        """);

    Query query =
        createNoShowDetailQuery(
                sql.toString(), tenantId, professionalId, from, to, serviceId, List.of(), clientQuery)
            .setParameter("limit", safeLimit);
    if (cursor != null) {
      query.setParameter("cursorDate", cursor.metricDate());
      query.setParameter("cursorStartTime", cursor.startTime());
      query.setParameter("cursorCreatedAt", cursor.createdAt());
      query.setParameter("cursorIdText", cursor.appointmentId().toString());
    }

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    return rows.stream().map(this::toNoShowItemRow).toList();
  }

  public List<NoShowItemRow> listarNoShowsDetalhadosParaExportacao(
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT
              appointment_id,
              client_id,
              client_name,
              client_phone,
              client_email,
              professional_id,
              professional_name,
              metric_date,
              start_time,
              end_time,
              status,
              notes,
              total_price,
              total_services,
              service_ids,
              service_names,
              created_at
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, from, to, serviceId, List.of(), clientQuery);
    sql.append(
        """

        ORDER BY n.metric_date DESC, n.start_time DESC, n.created_at DESC, n.appointment_id::text DESC
        """);
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        createNoShowDetailQuery(
                sql.toString(), tenantId, professionalId, from, to, serviceId, List.of(), clientQuery)
            .getResultList();
    return rows.stream().map(this::toNoShowItemRow).toList();
  }

  public Optional<NoShowCursorRow> findNoShowCursor(UUID tenantId, UUID appointmentId) {
    Object[] row =
        (Object[])
            entityManager
                .createNativeQuery(
                    """
                    SELECT appointment_id, metric_date, start_time, created_at
                    FROM mv_no_show_appointments
                    WHERE tenant_id = :tenantId
                      AND appointment_id = :appointmentId
                    """)
                .setParameter("tenantId", tenantId)
                .setParameter("appointmentId", appointmentId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    if (row == null) return Optional.empty();
    return Optional.of(
        new NoShowCursorRow(
            toUuid(row[0]), toLocalDate(row[1]), row[2] != null ? row[2].toString() : null, toInstant(row[3])));
  }

  /** Ultimo refresh da view materializada — exposto ao frontend como {@code lastUpdatedAt}. */
  public Instant findLastRefreshAt(String viewName) {
    Object value =
        entityManager
            .createNativeQuery(
                """
                SELECT refreshed_at
                FROM report_materialized_view_refresh_state
                WHERE view_name = :viewName
                """)
            .setParameter("viewName", viewName)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (value == null) return null;
    if (value instanceof Instant instant) return instant;
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof java.util.Date date) return date.toInstant();
    return null;
  }

  // ============================================================
  // Agregacoes auxiliares do resumo de no-show
  // ============================================================

  private long contarNoShowsNoPeriodo(
      UUID tenantId,
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(*)::bigint
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, from, to, serviceId, clientIds, clientQuery);
    Number value =
        (Number)
            createNoShowDetailQuery(
                    sql.toString(), tenantId, professionalId, from, to, serviceId, clientIds, clientQuery)
                .getSingleResult();
    return value != null ? value.longValue() : 0L;
  }

  private BigDecimal somarReceitaEmRiscoNoPeriodo(
      UUID tenantId,
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COALESCE(SUM(n.total_price), 0)
            FROM mv_no_show_appointments n
            WHERE n.tenant_id = :tenantId
            """);
    applyNoShowDetailFilters(sql, professionalId, from, to, serviceId, clientIds, clientQuery);
    Number value =
        (Number)
            createNoShowDetailQuery(
                    sql.toString(), tenantId, professionalId, from, to, serviceId, clientIds, clientQuery)
                .getSingleResult();
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal bd) return NumericUtil.normalize(bd);
    return NumericUtil.normalize(new BigDecimal(value.toString()));
  }

  private int contarAgendamentosConcluidos(
      UUID tenantId,
      LocalDate from,
      LocalDate to,
      UUID professionalId,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT COUNT(DISTINCT a.id)::int
            FROM appointments a
            LEFT JOIN clients c
              ON c.id = a.client_id
             AND c.tenant_id = a.tenant_id
            WHERE a.tenant_id = :tenantId
              AND a.status = 'Concluido'
            """);
    applyAppointmentFilters(sql, professionalId, from, to, serviceId, clientIds, clientQuery);
    Number value =
        (Number)
            createAppointmentAggregateQuery(
                    sql.toString(), tenantId, professionalId, from, to, serviceId, clientIds, clientQuery)
                .getSingleResult();
    return value != null ? value.intValue() : 0;
  }

  // ============================================================
  // Montagem de filtros / parametros
  // ============================================================

  private Query createNoShowDetailQuery(
      String sql,
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    Query query = entityManager.createNativeQuery(sql).setParameter("tenantId", tenantId);
    if (professionalId != null) query.setParameter("professionalId", professionalId);
    if (from != null) query.setParameter("from", from);
    if (to != null) query.setParameter("to", to);
    if (serviceId != null) query.setParameter("serviceIdText", serviceId.toString());
    if (clientIds != null && !clientIds.isEmpty()) query.setParameter("clientIds", clientIds);
    if (clientQuery != null && !clientQuery.isBlank()) {
      query.setParameter("clientQuery", "%" + clientQuery.trim().toLowerCase() + "%");
    }
    return query;
  }

  private Query createAppointmentAggregateQuery(
      String sql,
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    Query query = entityManager.createNativeQuery(sql).setParameter("tenantId", tenantId);
    if (professionalId != null) query.setParameter("professionalId", professionalId);
    if (from != null) query.setParameter("from", from);
    if (to != null) query.setParameter("to", to);
    if (serviceId != null) query.setParameter("serviceId", serviceId);
    if (clientIds != null && !clientIds.isEmpty()) query.setParameter("clientIds", clientIds);
    if (clientQuery != null && !clientQuery.isBlank()) {
      query.setParameter("clientQuery", "%" + clientQuery.trim().toLowerCase() + "%");
    }
    return query;
  }

  private Query createAppointmentManagementQuery(
      String sql,
      UUID tenantId,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String status) {
    Query query = entityManager.createNativeQuery(sql).setParameter("tenantId", tenantId);
    if (professionalId != null) query.setParameter("professionalId", professionalId);
    if (from != null) query.setParameter("from", from);
    if (to != null) query.setParameter("to", to);
    if (serviceId != null) query.setParameter("serviceId", serviceId);
    if (status != null && !status.isBlank()) query.setParameter("status", status);
    return query;
  }

  private void applyNoShowDetailFilters(
      StringBuilder sql,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    if (professionalId != null) {
      sql.append(" AND n.professional_id = :professionalId");
    }
    if (from != null) {
      sql.append(" AND n.metric_date >= :from");
    }
    if (to != null) {
      sql.append(" AND n.metric_date <= :to");
    }
    if (serviceId != null) {
      sql.append(" AND COALESCE(n.service_ids, '') LIKE '%' || :serviceIdText || '%'");
    }
    if (clientIds != null && !clientIds.isEmpty()) {
      sql.append(" AND n.client_id IN :clientIds");
    }
    if (clientQuery != null && !clientQuery.isBlank()) {
      sql.append(
          """
           AND (
                lower(coalesce(n.client_name, '')) like :clientQuery
             OR lower(coalesce(n.client_email, '')) like :clientQuery
             OR lower(coalesce(n.client_phone, '')) like :clientQuery
           )
          """);
    }
  }

  private void applyAppointmentFilters(
      StringBuilder sql,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      List<UUID> clientIds,
      String clientQuery) {
    if (professionalId != null) {
      sql.append(" AND a.professional_id = :professionalId");
    }
    if (from != null) {
      sql.append(" AND a.date >= :from");
    }
    if (to != null) {
      sql.append(" AND a.date <= :to");
    }
    if (serviceId != null) {
      sql.append(
          """
           AND EXISTS (
                SELECT 1
                FROM appointment_items ai
                WHERE ai.appointment_id = a.id
                  AND ai.tenant_id = a.tenant_id
                  AND ai.service_id = :serviceId
           )
          """);
    }
    if (clientIds != null && !clientIds.isEmpty()) {
      sql.append(" AND a.client_id IN :clientIds");
    }
    if (clientQuery != null && !clientQuery.isBlank()) {
      sql.append(
          """
           AND (
                lower(coalesce(c.name, '')) like :clientQuery
             OR lower(coalesce(c.email, '')) like :clientQuery
             OR lower(coalesce(c.phone, '')) like :clientQuery
           )
          """);
    }
  }

  private void applyAppointmentManagementFilters(
      StringBuilder sql,
      UUID professionalId,
      LocalDate from,
      LocalDate to,
      UUID serviceId,
      String status) {
    if (professionalId != null) {
      sql.append(" AND m.professional_id = :professionalId");
    }
    if (from != null) {
      sql.append(" AND m.data_agendamento >= :from");
    }
    if (to != null) {
      sql.append(" AND m.data_agendamento <= :to");
    }
    if (serviceId != null) {
      sql.append(" AND :serviceId = ANY(m.service_ids)");
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND m.status = :status");
    }
  }

  // ============================================================
  // Mapeamento de linhas
  // ============================================================

  private WeeklyRevenueRow toWeeklyRevenueRow(Object[] row) {
    WeeklyRevenueRow response = new WeeklyRevenueRow();
    response.date = toLocalDate(row[0]);
    response.revenue = toMoney(row[1]);
    return response;
  }

  private ServicePerformanceRow toServicePerformanceRow(Object[] row) {
    ServicePerformanceRow response = new ServicePerformanceRow();
    response.serviceId = row[0] == null ? null : toUuid(row[0]);
    response.serviceName = row[1] == null ? null : row[1].toString();
    response.totalAppointments = toInt(row[2]);
    response.completedAppointments = toInt(row[3]);
    response.canceledAppointments = toInt(row[4]);
    response.revenueTotal = toMoney(row[5]);
    return response;
  }

  private NoShowItemRow toNoShowItemRow(Object[] row) {
    NoShowItemRow response = new NoShowItemRow();
    response.appointmentId = toUuid(row[0]);
    response.clientId = toUuid(row[1]);
    response.clientName = row[2] != null ? row[2].toString() : null;
    response.clientPhone = row[3] != null ? row[3].toString() : null;
    response.clientEmail = row[4] != null ? row[4].toString() : null;
    response.professionalId = toUuid(row[5]);
    response.professionalName = row[6] != null ? row[6].toString() : null;
    response.date = toLocalDate(row[7]);
    response.startTime = row[8] != null ? row[8].toString() : null;
    response.endTime = row[9] != null ? row[9].toString() : null;
    response.status = row[10] != null ? row[10].toString() : null;
    response.notes = row[11] != null ? row[11].toString() : null;
    response.totalPrice = toMoney(row[12]);
    response.totalServices = toInt(row[13]);
    response.serviceNames = splitServiceNames(row[15] != null ? row[15].toString() : null);
    response.createdAt = toInstant(row[16]);
    return response;
  }

  private NoShowDailySummaryRow toNoShowDailySummaryRow(Object[] row) {
    NoShowDailySummaryRow response = new NoShowDailySummaryRow();
    response.date = toLocalDate(row[0]);
    response.totalNoShows = toInt(row[1]);
    response.revenueAtRisk = toMoney(row[2]);
    return response;
  }

  private NoShowGroupedSummaryRow toNoShowGroupedSummaryRow(Object[] row) {
    NoShowGroupedSummaryRow response = new NoShowGroupedSummaryRow();
    response.key = row[0] != null ? row[0].toString() : null;
    response.label = row[1] != null ? row[1].toString() : null;
    response.totalNoShows = toInt(row[2]);
    response.revenueAtRisk = toMoney(row[3]);
    return response;
  }

  private AppointmentManagementSummaryRow toAppointmentManagementSummaryRow(Object[] row) {
    AppointmentManagementSummaryRow response = new AppointmentManagementSummaryRow();
    response.totalAppointments = toInt(row[0]);
    response.totalConfirmed = toInt(row[1]);
    response.totalPending = toInt(row[2]);
    response.totalCancelled = toInt(row[3]);
    response.totalNoShow = toInt(row[4]);
    response.totalCompleted = toInt(row[5]);
    response.totalRevenue = toMoney(row[6]);
    response.totalGapOpportunities = toInt(row[7]);
    response.totalUnconfirmed = toInt(row[8]);
    response.totalAbandonmentSignalDays = toInt(row[9]);
    return response;
  }

  private AppointmentManagementItemRow toAppointmentManagementItemRow(Object[] row) {
    AppointmentManagementItemRow response = new AppointmentManagementItemRow();
    response.appointmentId = toUuid(row[0]);
    response.date = toLocalDate(row[1]);
    response.startTime = row[2] != null ? row[2].toString() : null;
    response.endTime = row[3] != null ? row[3].toString() : null;
    response.clientId = toUuid(row[4]);
    response.clientName = row[5] != null ? row[5].toString() : null;
    response.professionalId = toUuid(row[6]);
    response.professionalName = row[7] != null ? row[7].toString() : null;
    response.serviceLabel = row[8] != null ? row[8].toString() : null;
    response.status = row[9] != null ? row[9].toString() : null;
    response.origin = row[10] != null ? row[10].toString() : null;
    response.totalPrice = toMoney(row[11]);
    response.flagHorarioVago = toBoolean(row[12]);
    response.flagNaoConfirmado = toBoolean(row[13]);
    response.flagAbandonoFluxo = toBoolean(row[14]);
    return response;
  }

  private WhatsAppReactivationPointRow toWhatsAppReactivationPointRow(Object[] row) {
    WhatsAppReactivationPointRow response = new WhatsAppReactivationPointRow();
    response.metricDate = toLocalDate(row[0]);
    response.abandonedCount = toInt(row[1]);
    response.reactivatedCount = toInt(row[2]);
    response.convertedCount = toInt(row[3]);
    return response;
  }

  private List<String> splitServiceNames(String value) {
    if (value == null || value.isBlank()) return List.of();
    String[] parts = value.split("\\|\\|");
    List<String> response = new ArrayList<>();
    for (String part : parts) {
      if (part != null && !part.isBlank()) response.add(part.trim());
    }
    return response;
  }

  private BigDecimal toMoney(Object value) {
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal bd) return NumericUtil.normalize(bd);
    return NumericUtil.normalize(new BigDecimal(value.toString()));
  }

  private int toInt(Object value) {
    if (value == null) return 0;
    return ((Number) value).intValue();
  }

  private boolean toBoolean(Object value) {
    if (value == null) return false;
    if (value instanceof Boolean bool) return bool;
    String normalized = value.toString().trim();
    return "true".equalsIgnoreCase(normalized)
        || "t".equalsIgnoreCase(normalized)
        || "1".equals(normalized);
  }

  private UUID toUuid(Object value) {
    if (value == null) return null;
    if (value instanceof UUID uuid) return uuid;
    return UUID.fromString(value.toString());
  }

  private LocalDate toLocalDate(Object value) {
    if (value == null) return null;
    if (value instanceof LocalDate localDate) return localDate;
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
