package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Espelha {@code scheduler/ReportMaterializedViewRefreshService.java} do original: recalcula as
 * views materializadas que o painel e os relatorios leem ({@code RelatorioRepository},
 * {@code ServicoFinanceiro}).
 *
 * <p>Nao tinha sido portado: sem ele as views pararam no ultimo recalculo feito pelo Quarkus
 * (2026-09-11 em producao) e o painel mostrava zero para tudo o que veio depois (teste de relatorios
 * de 2026-09-18).
 *
 * <p>Cada view em sua propria conexao, fora de transacao ({@code REFRESH ... CONCURRENTLY} nao roda
 * dentro de uma), e a falha de uma nao impede as outras — igual ao original.
 */
@Service
public class ReportMaterializedViewRefreshService {

  private static final Logger LOG =
      LoggerFactory.getLogger(ReportMaterializedViewRefreshService.class);

  static final List<String> VIEWS =
      List.of(
          "mv_dashboard_metrics_daily",
          "mv_revenue_daily",
          "mv_dashboard_metrics_professional_daily",
          "mv_dashboard_service_metrics_daily",
          "mv_customer_top_services_daily",
          "mv_customer_service_rank_daily",
          "mv_appointment_booking_abandon_daily_stage",
          "mv_whatsapp_booking_reactivation_daily",
          "mv_finance_cashflow_daily",
          "mv_relatorio_agendamentos",
          "mv_no_show_appointments");

  private final DataSource dataSource;

  public ReportMaterializedViewRefreshService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /** @return quantas views foram recalculadas com sucesso */
  public int refreshMaterializedViews() {
    int atualizadas = 0;
    for (String viewName : VIEWS) {
      if (refreshConcurrently(viewName)) atualizadas++;
    }
    return atualizadas;
  }

  private boolean refreshConcurrently(String viewName) {
    // viewName vem so da lista fixa acima — nunca de entrada externa.
    String refresh = "REFRESH MATERIALIZED VIEW CONCURRENTLY " + viewName;
    String estado =
        """
        INSERT INTO report_materialized_view_refresh_state(view_name, refreshed_at)
        VALUES ('%s', CURRENT_TIMESTAMP)
        ON CONFLICT (view_name)
        DO UPDATE SET refreshed_at = EXCLUDED.refreshed_at
        """
            .formatted(viewName);
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(true);
      statement.execute(refresh);
      statement.execute(estado);
      return true;
    } catch (Exception e) {
      LOG.error("Falha ao atualizar materialized view: {}", viewName, e);
      return false;
    }
  }
}
