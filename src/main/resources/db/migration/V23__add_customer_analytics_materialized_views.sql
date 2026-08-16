CREATE MATERIALIZED VIEW mv_customer_top_services_daily
AS
SELECT
  a.tenant_id,
  a.client_id,
  ai.service_id,
  a.date AS metric_date,
  COUNT(DISTINCT a.id)::int AS completed_appointments,
  COALESCE(SUM(ai.quantity), 0)::int AS completed_services,
  COALESCE(SUM(ai.total_price), 0)::bigint AS revenue_total,
  MAX(a.date) AS last_appointment_date
FROM appointments a
JOIN appointment_items ai
  ON ai.tenant_id = a.tenant_id
 AND ai.appointment_id = a.id
WHERE a.status = 'Concluido'
GROUP BY a.tenant_id, a.client_id, ai.service_id, a.date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_customer_top_services_daily_tenant_client_service_date
  ON mv_customer_top_services_daily (tenant_id, client_id, service_id, metric_date);

CREATE INDEX idx_mv_customer_top_services_daily_tenant_client_date
  ON mv_customer_top_services_daily (tenant_id, client_id, metric_date DESC);

CREATE INDEX idx_mv_customer_top_services_daily_tenant_client_service_date
  ON mv_customer_top_services_daily (tenant_id, client_id, service_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_customer_top_services_daily;

CREATE MATERIALIZED VIEW mv_customer_service_rank_daily
AS
SELECT
  a.tenant_id,
  a.client_id,
  a.date AS metric_date,
  COUNT(DISTINCT a.id)::int AS completed_appointments,
  COALESCE(SUM(ai.quantity), 0)::int AS completed_services,
  COALESCE(SUM(ai.total_price), 0)::bigint AS revenue_total,
  MAX(a.date) AS last_appointment_date
FROM appointments a
JOIN appointment_items ai
  ON ai.tenant_id = a.tenant_id
 AND ai.appointment_id = a.id
WHERE a.status = 'Concluido'
GROUP BY a.tenant_id, a.client_id, a.date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_customer_service_rank_daily_tenant_client_date
  ON mv_customer_service_rank_daily (tenant_id, client_id, metric_date);

CREATE INDEX idx_mv_customer_service_rank_daily_tenant_date
  ON mv_customer_service_rank_daily (tenant_id, metric_date DESC);

CREATE INDEX idx_mv_customer_service_rank_daily_tenant_client_date
  ON mv_customer_service_rank_daily (tenant_id, client_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_customer_service_rank_daily;
