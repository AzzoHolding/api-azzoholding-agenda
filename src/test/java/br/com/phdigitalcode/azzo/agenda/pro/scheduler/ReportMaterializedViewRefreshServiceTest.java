package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O painel e os relatorios leem views materializadas; sem este recalculo elas ficaram paradas
 * desde a troca para o Spring (2026-09-18).
 */
class ReportMaterializedViewRefreshServiceTest {

  @Test
  @DisplayName("recalcula todas as views do painel e dos relatorios, e registra quando")
  void recalculaTodasAsViews() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);

    int atualizadas = new ReportMaterializedViewRefreshService(dataSource).refreshMaterializedViews();

    assertThat(atualizadas).isEqualTo(ReportMaterializedViewRefreshService.VIEWS.size());
    for (String view : ReportMaterializedViewRefreshService.VIEWS) {
      verify(statement).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY " + view);
    }
    assertThat(ReportMaterializedViewRefreshService.VIEWS).contains("mv_dashboard_metrics_daily");
  }

  @Test
  @DisplayName("a falha de uma view nao impede as outras")
  void falhaDeUmaViewNaoParaAsOutras() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    doThrow(new SQLException("sem indice unico"))
        .when(statement)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_revenue_daily");

    int atualizadas = new ReportMaterializedViewRefreshService(dataSource).refreshMaterializedViews();

    assertThat(atualizadas).isEqualTo(ReportMaterializedViewRefreshService.VIEWS.size() - 1);
    verify(statement).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_no_show_appointments");
    verify(statement, org.mockito.Mockito.atLeastOnce()).execute(startsWith("INSERT INTO report_materialized_view_refresh_state"));
  }
}
