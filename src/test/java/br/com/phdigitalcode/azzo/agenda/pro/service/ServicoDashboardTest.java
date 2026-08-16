package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardCustomerRankingResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardNoShowInsightsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardServiceAnalysisResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardWhatsAppReactivationResponse;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteAnalyticsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RelatorioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/** Testa {@link ServicoDashboard} — as regras de crescimento/percentual e a montagem dos responses. */
@ExtendWith(MockitoExtension.class)
class ServicoDashboardTest {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  @Mock private ContextoTenant contextoTenant;
  @Mock private ClienteAnalyticsRepository clienteAnalyticsRepository;
  @Mock private RelatorioRepository relatorioRepository;

  private ServicoDashboard service;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    service = new ServicoDashboard(contextoTenant, clienteAnalyticsRepository, relatorioRepository);
    tenantId = UUID.randomUUID();
    lenient().when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  @Nested
  @DisplayName("obterMetricas")
  class ObterMetricas {

    @Test
    @DisplayName("calcula crescimento percentual quando ha valor anterior positivo")
    void calculaCrescimento() {
      RelatorioRepository.DashboardMetricsRow row = new RelatorioRepository.DashboardMetricsRow();
      row.todayRevenue = new BigDecimal("150.00");
      row.previousTodayRevenue = new BigDecimal("100.00");
      row.monthlyRevenue = BigDecimal.ZERO;
      row.previousMonthlyRevenue = BigDecimal.ZERO;
      row.todayAppointments = 10;
      row.previousTodayAppointments = 5;
      row.totalClients = 20;
      row.previousTotalClients = 10;

      when(relatorioRepository.obterMetricasDashboard(eq(tenantId), any(), any(), any(), any(), any())).thenReturn(row);

      DashboardMetricsResponse response = service.obterMetricas();

      assertThat(response.todayRevenueGrowthPercent).isEqualTo(50.0);
      assertThat(response.todayAppointmentsGrowthPercent).isEqualTo(100.0);
      assertThat(response.totalClientsGrowthPercent).isEqualTo(100.0);
    }

    @Test
    @DisplayName("crescimento e null quando nao ha valor anterior (zero ou negativo)")
    void crescimentoNuloSemBase() {
      RelatorioRepository.DashboardMetricsRow row = new RelatorioRepository.DashboardMetricsRow();
      row.todayRevenue = new BigDecimal("50.00");
      row.previousTodayRevenue = BigDecimal.ZERO;
      row.monthlyRevenue = BigDecimal.ZERO;
      row.previousMonthlyRevenue = BigDecimal.ZERO;

      when(relatorioRepository.obterMetricasDashboard(eq(tenantId), any(), any(), any(), any(), any())).thenReturn(row);

      DashboardMetricsResponse response = service.obterMetricas();

      assertThat(response.todayRevenueGrowthPercent).isNull();
    }
  }

  @Nested
  @DisplayName("obterInsightsNoShow")
  class ObterInsightsNoShow {

    @Test
    @DisplayName("calcula taxa de no-show sobre concluidos + no-shows")
    void calculaTaxaNoShow() {
      RelatorioRepository.NoShowSummaryRow summary = new RelatorioRepository.NoShowSummaryRow();
      summary.totalNoShows = 2;
      summary.previousPeriodNoShows = 1;
      summary.lastSevenDaysNoShows = 1;
      summary.revenueAtRisk = new BigDecimal("200.00");
      summary.completedAppointments = 8;

      when(relatorioRepository.obterResumoNoShow(eq(tenantId), any(), any(), any(), any(), any())).thenReturn(summary);
      when(relatorioRepository.findLastRefreshAt("mv_no_show_appointments")).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
      when(relatorioRepository.listarNoShowsRecentes(eq(tenantId), any(), any(), eq(5))).thenReturn(List.of());

      DashboardNoShowInsightsResponse response = service.obterInsightsNoShow();

      // 2 no-shows / (8 concluidos + 2 no-shows) = 20%
      assertThat(response.noShowRate).isEqualTo(20.0);
      assertThat(response.totalNoShows).isEqualTo(2);
      assertThat(response.lastUpdatedAt).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("taxa e zero quando nao ha concluidos nem no-shows")
    void taxaZeroSemDados() {
      RelatorioRepository.NoShowSummaryRow summary = new RelatorioRepository.NoShowSummaryRow();
      summary.revenueAtRisk = BigDecimal.ZERO;

      when(relatorioRepository.obterResumoNoShow(eq(tenantId), any(), any(), any(), any(), any())).thenReturn(summary);
      when(relatorioRepository.listarNoShowsRecentes(eq(tenantId), any(), any(), eq(5))).thenReturn(List.of());

      DashboardNoShowInsightsResponse response = service.obterInsightsNoShow();

      assertThat(response.noShowRate).isEqualTo(0.0);
    }
  }

  @Nested
  @DisplayName("obterMetricasProfissional")
  class ObterMetricasProfissional {

    @Test
    @DisplayName("rejeita periodo invertido")
    void rejeitaPeriodoInvertido() {
      assertThatThrownBy(() -> service.obterMetricasProfissional("2026-02-10", "2026-02-01", null))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("obterAnaliseServicos")
  class ObterAnaliseServicos {

    @Test
    @DisplayName("identifica servico mais/menos pedido e mais cancelado/concluido")
    void identificaExtremos() {
      RelatorioRepository.ServicePerformanceRow corte = new RelatorioRepository.ServicePerformanceRow();
      corte.serviceId = UUID.randomUUID();
      corte.serviceName = "Corte";
      corte.totalAppointments = 20;
      corte.completedAppointments = 15;
      corte.canceledAppointments = 5;
      corte.revenueTotal = new BigDecimal("500.00");

      RelatorioRepository.ServicePerformanceRow escova = new RelatorioRepository.ServicePerformanceRow();
      escova.serviceId = UUID.randomUUID();
      escova.serviceName = "Escova";
      escova.totalAppointments = 5;
      escova.completedAppointments = 5;
      escova.canceledAppointments = 0;
      escova.revenueTotal = new BigDecimal("100.00");

      when(relatorioRepository.listarDesempenhoServicos(eq(tenantId), any(), any(), any()))
          .thenReturn(List.of(corte, escova));

      DashboardServiceAnalysisResponse response = service.obterAnaliseServicos(null, null, null);

      assertThat(response.mostRequestedService.serviceName).isEqualTo("Corte");
      assertThat(response.leastRequestedService.serviceName).isEqualTo("Escova");
      assertThat(response.mostCancelledService.serviceName).isEqualTo("Corte");
      assertThat(response.services).extracting(s -> s.serviceName).containsExactly("Corte", "Escova");
      // completionRate = 15/20 * 100 = 75
      assertThat(response.services.get(0).completionRate).isEqualTo(75.0);
    }
  }

  @Nested
  @DisplayName("obterRankingClientes")
  class ObterRankingClientes {

    @Test
    @DisplayName("delega para ClienteAnalyticsRepository com limit default 10")
    void delegaComLimitDefault() {
      when(clienteAnalyticsRepository.findLastRefreshAt("mv_customer_service_rank_daily")).thenReturn(null);
      when(clienteAnalyticsRepository.listCustomerRanking(eq(tenantId), any(), any(), eq(10))).thenReturn(List.of());

      DashboardCustomerRankingResponse response = service.obterRankingClientes(null, null, null);

      assertThat(response.items).isEmpty();
      assertThat(response.lastUpdatedAt).isNull();
    }

    @Test
    @DisplayName("rejeita periodo invertido")
    void rejeitaPeriodoInvertido() {
      assertThatThrownBy(() -> service.obterRankingClientes("2026-02-10", "2026-02-01", 5))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("obterMetricasReativacaoWhatsApp")
  class ObterMetricasReativacaoWhatsApp {

    @Test
    @DisplayName("taxa de reativacao e zero quando nao houve abandono")
    void taxaZeroSemAbandono() {
      RelatorioRepository.WhatsAppReactivationSummaryRow summary = new RelatorioRepository.WhatsAppReactivationSummaryRow();
      when(relatorioRepository.obterResumoReativacaoWhatsApp(eq(tenantId), any(), any())).thenReturn(summary);
      when(relatorioRepository.listarSerieReativacaoWhatsApp(eq(tenantId), any(), any())).thenReturn(List.of());

      DashboardWhatsAppReactivationResponse response = service.obterMetricasReativacaoWhatsApp(null);

      assertThat(response.reactivationRate).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calcula taxa de reativacao proporcional")
    void calculaTaxaReativacao() {
      RelatorioRepository.WhatsAppReactivationSummaryRow summary = new RelatorioRepository.WhatsAppReactivationSummaryRow();
      summary.totalAbandoned = 10;
      summary.totalReactivated = 4;
      when(relatorioRepository.obterResumoReativacaoWhatsApp(eq(tenantId), any(), any())).thenReturn(summary);
      when(relatorioRepository.listarSerieReativacaoWhatsApp(eq(tenantId), any(), any())).thenReturn(List.of());

      DashboardWhatsAppReactivationResponse response = service.obterMetricasReativacaoWhatsApp(30);

      assertThat(response.reactivationRate).isEqualTo(40.0);
    }
  }

  @Test
  @DisplayName("obterReceitaSemanal soma o total e calcula a media por ponto")
  void obterReceitaSemanalSomaEMedia() {
    RelatorioRepository.WeeklyRevenueRow dia1 = new RelatorioRepository.WeeklyRevenueRow();
    dia1.date = LocalDate.of(2026, 2, 1);
    dia1.revenue = new BigDecimal("100.00");
    RelatorioRepository.WeeklyRevenueRow dia2 = new RelatorioRepository.WeeklyRevenueRow();
    dia2.date = LocalDate.of(2026, 2, 2);
    dia2.revenue = new BigDecimal("50.00");

    when(relatorioRepository.listarReceitaDiaria(eq(tenantId), any(), any())).thenReturn(List.of(dia1, dia2));

    var response = service.obterReceitaSemanal("2026-02-01", "2026-02-02");

    assertThat(response.total).isEqualByComparingTo("150.00");
    assertThat(response.average).isEqualTo(75.0);
    assertThat(response.points).hasSize(2);
  }
}
