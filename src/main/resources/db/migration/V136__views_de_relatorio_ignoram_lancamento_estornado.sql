-- Lancamento estornado ou excluido nao e dinheiro: o estorno de uma comanda (e a exclusao de um
-- lancamento, V133) marcam deleted_at em transactions, mas as quatro views do painel e dos
-- relatorios somavam a linha mesmo assim. Recriadas com o mesmo formato e indices da V104, agora
-- ignorando deleted_at (teste de relatorios de 2026-09-18).

DROP MATERIALIZED VIEW IF EXISTS mv_finance_cashflow_daily CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_metrics_daily CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_revenue_daily CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_metrics_professional_daily CASCADE;

-- mv_finance_cashflow_daily
CREATE MATERIALIZED VIEW mv_finance_cashflow_daily AS
SELECT
  tenant_id,
  (date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
  SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE 0 END)       AS total_income,
  SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END)       AS total_expenses,
  SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE -amount END) AS balance
FROM transactions
WHERE deleted_at IS NULL
GROUP BY tenant_id, (date AT TIME ZONE 'America/Sao_Paulo')::date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_finance_cashflow_daily
  ON mv_finance_cashflow_daily (tenant_id, metric_date);
CREATE INDEX idx_mv_finance_cashflow_daily_tenant_date
  ON mv_finance_cashflow_daily (tenant_id, metric_date DESC);

-- mv_dashboard_metrics_daily
CREATE MATERIALIZED VIEW mv_dashboard_metrics_daily AS
WITH today_ctx AS (
  SELECT (NOW() AT TIME ZONE 'America/Sao_Paulo')::date AS today
),
transaction_daily AS (
  SELECT
    t.tenant_id,
    (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    COALESCE(SUM(t.amount) FILTER (WHERE t.type = 'INCOME'), 0) AS today_revenue
  FROM transactions t
  WHERE t.deleted_at IS NULL
  GROUP BY t.tenant_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
),
appointment_daily AS (
  SELECT
    a.tenant_id,
    a.date AS metric_date,
    COUNT(*)::int AS today_appointments,
    COUNT(*) FILTER (WHERE a.status = 'Pendente')::int AS pending_appointments,
    COUNT(*) FILTER (WHERE a.status = 'Concluido')::int AS completed_today
  FROM appointments a
  GROUP BY a.tenant_id, a.date
),
client_totals AS (
  SELECT
    c.tenant_id,
    COUNT(*)::int AS total_clients
  FROM clients c
  GROUP BY c.tenant_id
),
activity_dates AS (
  SELECT tenant_id, metric_date FROM transaction_daily
  UNION
  SELECT tenant_id, metric_date FROM appointment_daily
),
tenant_dates AS (
  SELECT tenant_id, metric_date FROM activity_dates
  UNION
  SELECT t.id AS tenant_id, ctx.today AS metric_date
  FROM tenants t
  CROSS JOIN today_ctx ctx
)
SELECT
  td.tenant_id,
  td.metric_date,
  COALESCE(tx.today_revenue, 0)   AS today_revenue,
  COALESCE(ap.today_appointments, 0)::int AS today_appointments,
  COALESCE(ap.pending_appointments, 0)::int AS pending_appointments,
  COALESCE(ap.completed_today, 0)::int AS completed_today,
  COALESCE(ct.total_clients, 0)::int AS total_clients
FROM tenant_dates td
LEFT JOIN transaction_daily tx
  ON tx.tenant_id = td.tenant_id
 AND tx.metric_date = td.metric_date
LEFT JOIN appointment_daily ap
  ON ap.tenant_id = td.tenant_id
 AND ap.metric_date = td.metric_date
LEFT JOIN client_totals ct
  ON ct.tenant_id = td.tenant_id
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_dashboard_metrics_daily_tenant_date
  ON mv_dashboard_metrics_daily (tenant_id, metric_date);
CREATE INDEX idx_mv_dashboard_metrics_daily_date
  ON mv_dashboard_metrics_daily (metric_date);
CREATE INDEX idx_mv_dashboard_metrics_daily_tenant_date
  ON mv_dashboard_metrics_daily (tenant_id, metric_date DESC);

-- mv_revenue_daily
CREATE MATERIALIZED VIEW mv_revenue_daily AS
SELECT
  t.tenant_id,
  (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
  SUM(t.amount) AS revenue
FROM transactions t
WHERE t.type = 'INCOME'
  AND t.deleted_at IS NULL
GROUP BY t.tenant_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_revenue_daily_tenant_date
  ON mv_revenue_daily (tenant_id, metric_date);
CREATE INDEX idx_mv_revenue_daily_metric_date
  ON mv_revenue_daily (metric_date);
CREATE INDEX idx_mv_revenue_daily_tenant_date
  ON mv_revenue_daily (tenant_id, metric_date DESC);

-- mv_dashboard_metrics_professional_daily
CREATE MATERIALIZED VIEW mv_dashboard_metrics_professional_daily AS
WITH appointment_income_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    a.date AS metric_date,
    COALESCE(SUM(ai.total_price), 0) AS revenue
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
    COALESCE(SUM(t.amount), 0) AS commission
  FROM transactions t
  JOIN appointments a
    ON a.id = t.appointment_id
  JOIN transaction_categories tc
    ON tc.id = t.category_id
   AND tc.tenant_id = t.tenant_id
  WHERE t.type = 'EXPENSE'
    AND t.deleted_at IS NULL
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
  COALESCE(rd.revenue, 0)     AS revenue,
  COALESCE(cd.commission, 0)  AS commission,
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

REFRESH MATERIALIZED VIEW mv_finance_cashflow_daily;
REFRESH MATERIALIZED VIEW mv_dashboard_metrics_daily;
REFRESH MATERIALIZED VIEW mv_revenue_daily;
REFRESH MATERIALIZED VIEW mv_dashboard_metrics_professional_daily;

INSERT INTO report_materialized_view_refresh_state (view_name, refreshed_at)
VALUES ('mv_finance_cashflow_daily', CURRENT_TIMESTAMP),
       ('mv_dashboard_metrics_daily', CURRENT_TIMESTAMP),
       ('mv_revenue_daily', CURRENT_TIMESTAMP),
       ('mv_dashboard_metrics_professional_daily', CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO UPDATE SET refreshed_at = EXCLUDED.refreshed_at;
