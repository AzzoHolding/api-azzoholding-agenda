package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardCustomerRankingResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardNoShowInsightsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardProfessionalMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardServiceAnalysisResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardWhatsAppReactivationResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.WeeklyRevenueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteAnalyticsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RelatorioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.util.DataUtil;
import br.com.phdigitalcode.azzo.agenda.pro.util.NumericUtil;
import org.springframework.transaction.annotation.Transactional;

/**
 * Espelha {@code modules/reports/application/ServicoDashboard.java}.
 *
 * <p><b>Escopo desta etapa</b>: todos os metodos do original exceto
 * {@code listarFilaReativacaoWhatsApp} (fila operacional ao vivo, endpoint
 * {@code /api/v1/dashboard/metrics/whatsapp-reactivation/queue}) — esse metodo le entidades do
 * modulo {@code chat} ({@code WhatsAppBookingReactivationCycleEntity}/{@code AttemptEntity} e seus
 * repositorios), que ainda nao foi portado para o Spring. {@code obterMetricasReativacaoWhatsApp}
 * (series/resumo) foi portado normalmente porque le apenas a view materializada
 * {@code mv_whatsapp_booking_reactivation_daily} via {@link RelatorioRepository} — nao depende do
 * modulo {@code chat}. Ver MIGRACAO-QUARKUS-SPRING.md, secao "reports", para o registro desta
 * fronteira.
 */
@Service
public class ServicoDashboard {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  private final ContextoTenant contextoTenant;
  private final ClienteAnalyticsRepository clienteAnalyticsRepository;
  private final RelatorioRepository relatorioRepository;

  public ServicoDashboard(
      ContextoTenant contextoTenant,
      ClienteAnalyticsRepository clienteAnalyticsRepository,
      RelatorioRepository relatorioRepository) {
    this.contextoTenant = contextoTenant;
    this.clienteAnalyticsRepository = clienteAnalyticsRepository;
    this.relatorioRepository = relatorioRepository;
  }

  @Transactional(readOnly = true)
  public DashboardMetricsResponse obterMetricas() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONA_BR);
    LocalDate inicioMes = hoje.withDayOfMonth(1);
    LocalDate ontem = hoje.minusDays(1);
    LocalDate inicioMesAnterior = inicioMes.minusMonths(1);
    LocalDate fimMesAnterior = inicioMesAnterior.plusMonths(1).minusDays(1);
    int diaCorrente = hoje.getDayOfMonth();
    LocalDate fimComparacaoMesAnterior = inicioMesAnterior.plusDays(Math.max(diaCorrente - 1, 0));
    if (fimComparacaoMesAnterior.isAfter(fimMesAnterior)) {
      fimComparacaoMesAnterior = fimMesAnterior;
    }

    RelatorioRepository.DashboardMetricsRow row =
        relatorioRepository.obterMetricasDashboard(
            tenantId, inicioMes, hoje, ontem, inicioMesAnterior, fimComparacaoMesAnterior);

    DashboardMetricsResponse r = new DashboardMetricsResponse();
    r.todayRevenue = row.todayRevenue;
    r.monthlyRevenue = row.monthlyRevenue;
    r.todayAppointments = row.todayAppointments;
    r.todayRevenueGrowthPercent = calcularVariacaoPercentual(row.todayRevenue, row.previousTodayRevenue);
    r.monthlyRevenueGrowthPercent = calcularVariacaoPercentual(row.monthlyRevenue, row.previousMonthlyRevenue);
    r.todayAppointmentsGrowthPercent =
        calcularVariacaoPercentual(row.todayAppointments, row.previousTodayAppointments);
    r.totalClientsGrowthPercent = calcularVariacaoPercentual(row.totalClients, row.previousTotalClients);
    r.pendingAppointments = row.pendingAppointments;
    r.completedToday = row.completedToday;
    r.totalClients = row.totalClients;
    r.notConcludedToday = row.notConcludedToday;
    r.stoppedAtServiceSelection = row.stoppedAtServiceSelection;
    r.stoppedAtProfessionalSelection = row.stoppedAtProfessionalSelection;
    r.stoppedAtTimeSelection = row.stoppedAtTimeSelection;
    r.stoppedAtFinalReview = row.stoppedAtFinalReview;
    r.whatsAppOpenFlowsToday = row.whatsAppOpenFlowsToday;
    r.whatsAppStoppedAtServiceSelection = row.whatsAppStoppedAtServiceSelection;
    r.whatsAppStoppedAtProfessionalSelection = row.whatsAppStoppedAtProfessionalSelection;
    r.whatsAppStoppedAtTimeSelection = row.whatsAppStoppedAtTimeSelection;
    r.whatsAppStoppedAtFinalReview = row.whatsAppStoppedAtFinalReview;
    return r;
  }

  @Transactional(readOnly = true)
  public WeeklyRevenueResponse obterReceitaSemanal(String start, String end) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate inicio =
        start == null || start.isBlank() ? LocalDate.now(ZONA_BR).minusDays(6) : DataUtil.parseDataISO(start);
    LocalDate fim = end == null || end.isBlank() ? LocalDate.now(ZONA_BR) : DataUtil.parseDataISO(end);

    WeeklyRevenueResponse resp = new WeeklyRevenueResponse();
    for (RelatorioRepository.WeeklyRevenueRow row : relatorioRepository.listarReceitaDiaria(tenantId, inicio, fim)) {
      WeeklyRevenueResponse.Point point = new WeeklyRevenueResponse.Point();
      point.day = row.date.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("pt", "BR"));
      point.date = row.date.toString();
      point.value = row.revenue;
      resp.points.add(point);
      resp.total = NumericUtil.add(resp.total, row.revenue);
    }

    resp.average = resp.points.isEmpty() ? 0 : resp.total.doubleValue() / resp.points.size();
    return resp;
  }

  @Transactional(readOnly = true)
  public DashboardProfessionalMetricsResponse obterMetricasProfissional(String start, String end, String professionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONA_BR);
    LocalDate inicio = start == null || start.isBlank() ? hoje.withDayOfMonth(1) : DataUtil.parseDataISO(start);
    LocalDate fim = end == null || end.isBlank() ? hoje : DataUtil.parseDataISO(end);
    if (inicio.isAfter(fim)) {
      throw new IllegalArgumentException("Data inicial nao pode ser maior que data final.");
    }

    UUID professionalUuid = (professionalId == null || professionalId.isBlank()) ? null : UUID.fromString(professionalId);

    RelatorioRepository.DashboardProfessionalMetricsRow row =
        relatorioRepository.obterMetricasDashboardProfissional(tenantId, professionalUuid, inicio, fim);

    DashboardProfessionalMetricsResponse response = new DashboardProfessionalMetricsResponse();
    response.startDate = inicio.toString();
    response.endDate = fim.toString();
    response.professionalId = professionalUuid == null ? null : professionalUuid.toString();
    response.revenueTotal = row.revenueTotal;
    response.commissionTotal = row.commissionTotal;
    response.completedServices = row.completedServices;
    response.clientsServed = row.clientsServed;
    return response;
  }

  @Transactional(readOnly = true)
  public DashboardServiceAnalysisResponse obterAnaliseServicos(String start, String end, String professionalId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONA_BR);
    LocalDate inicio = start == null || start.isBlank() ? hoje.withDayOfMonth(1) : DataUtil.parseDataISO(start);
    LocalDate fim = end == null || end.isBlank() ? hoje : DataUtil.parseDataISO(end);
    if (inicio.isAfter(fim)) {
      throw new IllegalArgumentException("Data inicial nao pode ser maior que data final.");
    }

    UUID professionalUuid = (professionalId == null || professionalId.isBlank()) ? null : UUID.fromString(professionalId);

    DashboardServiceAnalysisResponse response = new DashboardServiceAnalysisResponse();
    response.startDate = inicio.toString();
    response.endDate = fim.toString();
    response.professionalId = professionalUuid == null ? null : professionalUuid.toString();

    for (RelatorioRepository.ServicePerformanceRow row :
        relatorioRepository.listarDesempenhoServicos(tenantId, professionalUuid, inicio, fim)) {
      DashboardServiceAnalysisResponse.ServiceMetric item = new DashboardServiceAnalysisResponse.ServiceMetric();
      item.serviceId = row.serviceId == null ? null : row.serviceId.toString();
      item.serviceName = row.serviceName;
      item.totalAppointments = row.totalAppointments;
      item.completedAppointments = row.completedAppointments;
      item.canceledAppointments = row.canceledAppointments;
      item.revenueTotal = row.revenueTotal;
      item.completionRate = calcularPercentual(row.completedAppointments, row.totalAppointments);
      item.cancellationRate = calcularPercentual(row.canceledAppointments, row.totalAppointments);
      response.services.add(item);
    }

    response.mostRequestedService =
        response.services.stream().max(Comparator.comparingInt(item -> item.totalAppointments)).orElse(null);
    response.leastRequestedService =
        response.services.stream().min(Comparator.comparingInt(item -> item.totalAppointments)).orElse(null);
    response.mostCancelledService =
        response.services.stream().max(Comparator.comparingInt(item -> item.canceledAppointments)).orElse(null);
    response.mostCompletedService =
        response.services.stream().max(Comparator.comparingInt(item -> item.completedAppointments)).orElse(null);
    return response;
  }

  @Transactional(readOnly = true)
  public DashboardCustomerRankingResponse obterRankingClientes(String start, String end, Integer limit) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONA_BR);
    LocalDate inicio = start == null || start.isBlank() ? hoje.withDayOfMonth(1) : DataUtil.parseDataISO(start);
    LocalDate fim = end == null || end.isBlank() ? hoje : DataUtil.parseDataISO(end);
    if (inicio.isAfter(fim)) {
      throw new IllegalArgumentException("Data inicial nao pode ser maior que data final.");
    }

    DashboardCustomerRankingResponse response = new DashboardCustomerRankingResponse();
    response.startDate = inicio.toString();
    response.endDate = fim.toString();
    Instant lastRefreshAt = clienteAnalyticsRepository.findLastRefreshAt("mv_customer_service_rank_daily");
    response.lastUpdatedAt = lastRefreshAt != null ? lastRefreshAt.toString() : null;
    response.items.addAll(clienteAnalyticsRepository.listCustomerRanking(tenantId, inicio, fim, limit == null ? 10 : limit));
    return response;
  }

  @Transactional(readOnly = true)
  public DashboardWhatsAppReactivationResponse obterMetricasReativacaoWhatsApp(Integer days) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int lookbackDays = days == null || days < 7 ? 30 : Math.min(days, 90);
    LocalDate end = LocalDate.now(ZONA_BR);
    LocalDate start = end.minusDays(lookbackDays - 1L);

    RelatorioRepository.WhatsAppReactivationSummaryRow summary =
        relatorioRepository.obterResumoReativacaoWhatsApp(tenantId, start, end);

    DashboardWhatsAppReactivationResponse response = new DashboardWhatsAppReactivationResponse();
    response.startDate = start.toString();
    response.endDate = end.toString();
    response.totalAbandoned = summary.totalAbandoned;
    response.totalReactivated = summary.totalReactivated;
    response.totalConverted = summary.totalConverted;
    response.reactivationRate =
        summary.totalAbandoned <= 0 ? 0.0 : (summary.totalReactivated * 100.0) / summary.totalAbandoned;
    response.stoppedAtServiceSelection = summary.stoppedAtServiceSelection;
    response.stoppedAtProfessionalSelection = summary.stoppedAtProfessionalSelection;
    response.stoppedAtTimeSelection = summary.stoppedAtTimeSelection;
    response.stoppedAtFinalReview = summary.stoppedAtFinalReview;

    for (RelatorioRepository.WhatsAppReactivationPointRow row :
        relatorioRepository.listarSerieReativacaoWhatsApp(tenantId, start, end)) {
      DashboardWhatsAppReactivationResponse.Point point = new DashboardWhatsAppReactivationResponse.Point();
      point.metricDate = row.metricDate != null ? row.metricDate.toString() : null;
      point.abandonedCount = row.abandonedCount;
      point.reactivatedCount = row.reactivatedCount;
      point.convertedCount = row.convertedCount;
      response.points.add(point);
    }
    return response;
  }

  @Transactional(readOnly = true)
  public DashboardNoShowInsightsResponse obterInsightsNoShow() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LocalDate hoje = LocalDate.now(ZONA_BR);
    LocalDate inicio = hoje.withDayOfMonth(1);
    LocalDate previousStart = inicio.minusMonths(1);
    LocalDate previousEnd = previousStart.plusDays(Math.min(hoje.getDayOfMonth(), previousStart.lengthOfMonth()) - 1L);
    LocalDate lastSevenDaysStart = hoje.minusDays(6);

    RelatorioRepository.NoShowSummaryRow summary =
        relatorioRepository.obterResumoNoShow(tenantId, inicio, hoje, previousStart, previousEnd, lastSevenDaysStart);

    DashboardNoShowInsightsResponse response = new DashboardNoShowInsightsResponse();
    response.startDate = inicio.toString();
    response.endDate = hoje.toString();
    response.lastUpdatedAt =
        Optional.ofNullable(relatorioRepository.findLastRefreshAt("mv_no_show_appointments"))
            .map(Instant::toString)
            .orElse(null);
    response.totalNoShows = summary.totalNoShows;
    response.previousPeriodNoShows = summary.previousPeriodNoShows;
    int denominator = summary.completedAppointments + summary.totalNoShows;
    response.noShowRate = denominator <= 0 ? 0.0 : (summary.totalNoShows * 100.0) / denominator;
    response.lastSevenDaysNoShows = summary.lastSevenDaysNoShows;
    response.revenueAtRisk = summary.revenueAtRisk;
    for (RelatorioRepository.NoShowItemRow row : relatorioRepository.listarNoShowsRecentes(tenantId, inicio, hoje, 5)) {
      DashboardNoShowInsightsResponse.Item item = new DashboardNoShowInsightsResponse.Item();
      item.appointmentId = row.appointmentId != null ? row.appointmentId.toString() : null;
      item.clientId = row.clientId != null ? row.clientId.toString() : null;
      item.clientName = row.clientName;
      item.professionalId = row.professionalId != null ? row.professionalId.toString() : null;
      item.professionalName = row.professionalName;
      item.date = row.date != null ? row.date.toString() : null;
      item.startTime = row.startTime;
      item.endTime = row.endTime;
      item.totalPrice = row.totalPrice;
      item.status = "NO_SHOW";
      item.serviceNames = row.serviceNames;
      response.recentItems.add(item);
    }
    return response;
  }

  private double calcularPercentual(int parcial, int total) {
    if (total <= 0 || parcial <= 0) return 0.0;
    return (parcial * 100.0) / total;
  }

  private Double calcularVariacaoPercentual(BigDecimal atual, BigDecimal anterior) {
    if (anterior == null || anterior.compareTo(BigDecimal.ZERO) <= 0) return null;
    return atual.subtract(anterior)
        .multiply(BigDecimal.valueOf(100))
        .divide(anterior, 4, RoundingMode.HALF_UP)
        .doubleValue();
  }

  private Double calcularVariacaoPercentual(long atual, long anterior) {
    if (anterior <= 0L) return null;
    return ((atual - anterior) * 100.0) / anterior;
  }
}
