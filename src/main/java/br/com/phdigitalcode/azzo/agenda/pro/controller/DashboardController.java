package br.com.phdigitalcode.azzo.agenda.pro.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardCustomerRankingResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardNoShowInsightsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardProfessionalMetricsResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardServiceAnalysisResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.DashboardWhatsAppReactivationResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.response.WeeklyRevenueResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.RequiresPermission;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDashboard;

/**
 * Espelha {@code modules/reports/api/DashboardResource.java} (rotas {@code /api/v1/dashboard/*}).
 *
 * <p><b>Endpoint nao portado nesta etapa</b>: {@code GET /metrics/whatsapp-reactivation/queue} —
 * depende de {@code ServicoDashboard.listarFilaReativacaoWhatsApp}, que le entidades do modulo
 * {@code chat} (ainda nao portado para o Spring). Ver Javadoc de {@link ServicoDashboard}.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('OWNER', 'PROFESSIONAL')")
public class DashboardController {

  private final ServicoDashboard servicoDashboard;

  public DashboardController(ServicoDashboard servicoDashboard) {
    this.servicoDashboard = servicoDashboard;
  }

  @GetMapping("/metrics")
  @RequiresPermission("dashboard:view")
  public DashboardMetricsResponse metrics() {
    return servicoDashboard.obterMetricas();
  }

  @GetMapping("/metrics/professional")
  @RequiresPermission("dashboard:view")
  public DashboardProfessionalMetricsResponse professionalMetrics(
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end,
      @RequestParam(required = false) String professionalId) {
    return servicoDashboard.obterMetricasProfissional(start, end, professionalId);
  }

  @GetMapping("/metrics/services")
  @RequiresPermission("dashboard:view")
  public DashboardServiceAnalysisResponse serviceMetrics(
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end,
      @RequestParam(required = false) String professionalId) {
    return servicoDashboard.obterAnaliseServicos(start, end, professionalId);
  }

  @GetMapping("/metrics/customers")
  @RequiresPermission("dashboard:view")
  public DashboardCustomerRankingResponse customerMetrics(
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end,
      @RequestParam(required = false) Integer limit) {
    return servicoDashboard.obterRankingClientes(start, end, limit);
  }

  @GetMapping("/metrics/no-show")
  @RequiresPermission("dashboard:view")
  public DashboardNoShowInsightsResponse noShowMetrics() {
    return servicoDashboard.obterInsightsNoShow();
  }

  @GetMapping("/metrics/whatsapp-reactivation")
  @RequiresPermission("dashboard:view")
  public DashboardWhatsAppReactivationResponse whatsAppReactivationMetrics(
      @RequestParam(required = false) Integer days) {
    return servicoDashboard.obterMetricasReativacaoWhatsApp(days);
  }

  @GetMapping("/revenue/weekly")
  @RequiresPermission("dashboard:view")
  public WeeklyRevenueResponse weeklyRevenue(
      @RequestParam(required = false) String start, @RequestParam(required = false) String end) {
    return servicoDashboard.obterReceitaSemanal(start, end);
  }
}
