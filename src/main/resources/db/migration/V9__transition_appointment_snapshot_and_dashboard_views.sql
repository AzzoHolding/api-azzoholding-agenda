ALTER TABLE appointments
  ALTER COLUMN service_id DROP NOT NULL;

ALTER TABLE appointments
  ALTER COLUMN total_price DROP NOT NULL;

DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_metrics_professional_daily;

CREATE MATERIALIZED VIEW mv_dashboard_metrics_professional_daily
AS
WITH appointment_income_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    a.date AS metric_date,
    COALESCE(SUM(ai.total_price), 0)::bigint AS revenue
  FROM appointments a
  JOIN appointment_items ai
    ON ai.tenant_id = a.tenant_id
   AND ai.appointment_id = a.id
  WHERE a.status = 'Concluido'
  GROUP BY a.tenant_id, a.professional_id, a.date
),
commission_daily AS (
  SELECT
    t.tenant_id,
    a.professional_id,
    (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    COALESCE(SUM(t.amount), 0)::bigint AS commission
  FROM transactions t
  JOIN appointments a
    ON a.id = t.appointment_id
  JOIN transaction_categories tc
    ON tc.id = t.category_id
   AND tc.tenant_id = t.tenant_id
  WHERE t.type = 'EXPENSE'
    AND tc.name = 'COMMISSION'
  GROUP BY t.tenant_id, a.professional_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
),
appointments_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    a.date AS metric_date,
    COUNT(*) FILTER (WHERE a.status = 'Concluido')::int AS completed_services,
    COUNT(DISTINCT a.client_id) FILTER (WHERE a.status = 'Concluido')::int AS clients_served
  FROM appointments a
  GROUP BY a.tenant_id, a.professional_id, a.date
),
activity_dates AS (
  SELECT tenant_id, professional_id, metric_date FROM appointment_income_daily
  UNION
  SELECT tenant_id, professional_id, metric_date FROM commission_daily
  UNION
  SELECT tenant_id, professional_id, metric_date FROM appointments_daily
)
SELECT
  ad.tenant_id,
  ad.professional_id,
  ad.metric_date,
  COALESCE(rd.revenue, 0)::bigint AS revenue,
  COALESCE(cd.commission, 0)::bigint AS commission,
  COALESCE(ap.completed_services, 0)::int AS completed_services,
  COALESCE(ap.clients_served, 0)::int AS clients_served
FROM activity_dates ad
LEFT JOIN appointment_income_daily rd
  ON rd.tenant_id = ad.tenant_id
 AND rd.professional_id = ad.professional_id
 AND rd.metric_date = ad.metric_date
LEFT JOIN commission_daily cd
  ON cd.tenant_id = ad.tenant_id
 AND cd.professional_id = ad.professional_id
 AND cd.metric_date = ad.metric_date
LEFT JOIN appointments_daily ap
  ON ap.tenant_id = ad.tenant_id
 AND ap.professional_id = ad.professional_id
 AND ap.metric_date = ad.metric_date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_dashboard_metrics_prof_daily_tenant_prof_date
  ON mv_dashboard_metrics_professional_daily (tenant_id, professional_id, metric_date);

CREATE INDEX idx_mv_dashboard_metrics_prof_daily_tenant_date
  ON mv_dashboard_metrics_professional_daily (tenant_id, metric_date DESC);

CREATE INDEX idx_mv_dashboard_metrics_prof_daily_tenant_prof_date
  ON mv_dashboard_metrics_professional_daily (tenant_id, professional_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_dashboard_metrics_professional_daily;

DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_service_metrics_daily;

CREATE MATERIALIZED VIEW mv_dashboard_service_metrics_daily
AS
WITH appointments_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    ai.service_id,
    a.date AS metric_date,
    COUNT(DISTINCT a.id)::int AS total_appointments,
    COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Concluido')::int AS completed_appointments,
    COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Cancelado')::int AS canceled_appointments,
    COALESCE(SUM(ai.total_price) FILTER (WHERE a.status = 'Concluido'), 0)::bigint AS revenue
  FROM appointments a
  JOIN appointment_items ai
    ON ai.tenant_id = a.tenant_id
   AND ai.appointment_id = a.id
  GROUP BY a.tenant_id, a.professional_id, ai.service_id, a.date
)
SELECT
  ad.tenant_id,
  ad.professional_id,
  ad.service_id,
  ad.metric_date,
  ad.total_appointments,
  ad.completed_appointments,
  ad.canceled_appointments,
  ad.revenue
FROM appointments_daily ad
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_dashboard_service_metrics_daily_tenant_prof_service_date
  ON mv_dashboard_service_metrics_daily (tenant_id, professional_id, service_id, metric_date);

CREATE INDEX idx_mv_dashboard_service_metrics_daily_tenant_date
  ON mv_dashboard_service_metrics_daily (tenant_id, metric_date DESC);

CREATE INDEX idx_mv_dashboard_service_metrics_daily_tenant_prof_date
  ON mv_dashboard_service_metrics_daily (tenant_id, professional_id, metric_date DESC);

CREATE INDEX idx_mv_dashboard_service_metrics_daily_tenant_service_date
  ON mv_dashboard_service_metrics_daily (tenant_id, service_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_dashboard_service_metrics_daily;
