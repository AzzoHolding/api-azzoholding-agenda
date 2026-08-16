-- Popula as materialized views recriadas pela V104 (WITH NO DATA).
-- O scheduler automatico so roda em intervalos fixos; esta migration
-- garante que as views estejam disponiveis imediatamente apos o deploy.

REFRESH MATERIALIZED VIEW mv_finance_cashflow_daily;
REFRESH MATERIALIZED VIEW mv_dashboard_metrics_daily;
REFRESH MATERIALIZED VIEW mv_revenue_daily;
REFRESH MATERIALIZED VIEW mv_dashboard_metrics_professional_daily;
REFRESH MATERIALIZED VIEW mv_dashboard_service_metrics_daily;
REFRESH MATERIALIZED VIEW mv_no_show_appointments;
REFRESH MATERIALIZED VIEW mv_customer_top_services_daily;
REFRESH MATERIALIZED VIEW mv_customer_service_rank_daily;
REFRESH MATERIALIZED VIEW mv_relatorio_agendamentos;

UPDATE report_materialized_view_refresh_state
SET refreshed_at = CURRENT_TIMESTAMP
WHERE view_name IN (
  'mv_finance_cashflow_daily',
  'mv_dashboard_metrics_daily',
  'mv_revenue_daily',
  'mv_dashboard_metrics_professional_daily',
  'mv_dashboard_service_metrics_daily',
  'mv_no_show_appointments',
  'mv_customer_top_services_daily',
  'mv_customer_service_rank_daily',
  'mv_relatorio_agendamentos'
);
