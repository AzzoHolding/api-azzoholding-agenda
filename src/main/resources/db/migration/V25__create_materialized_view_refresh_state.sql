CREATE TABLE IF NOT EXISTS report_materialized_view_refresh_state (
  view_name VARCHAR(150) PRIMARY KEY,
  refreshed_at TIMESTAMP NOT NULL
);

INSERT INTO report_materialized_view_refresh_state (view_name, refreshed_at)
VALUES
  ('mv_dashboard_metrics_daily', CURRENT_TIMESTAMP),
  ('mv_revenue_daily', CURRENT_TIMESTAMP),
  ('mv_dashboard_metrics_professional_daily', CURRENT_TIMESTAMP),
  ('mv_dashboard_service_metrics_daily', CURRENT_TIMESTAMP),
  ('mv_customer_top_services_daily', CURRENT_TIMESTAMP),
  ('mv_customer_service_rank_daily', CURRENT_TIMESTAMP),
  ('mv_appointment_booking_abandon_daily_stage', CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO NOTHING;
