-- Converte colunas monetárias de BIGINT (centavos) para NUMERIC(19,2) (reais).
--
-- Impacto: 8 materialized views dependem dessas colunas e precisam ser
-- recriadas sem os casts ::bigint para que o tipo de retorno reflita NUMERIC.
-- O Java foi atualizado para ler BigDecimal diretamente, sem fromCents().

-- ─── 1. DROP de todas as views dependentes (CASCADE remove índices) ────────────

DROP MATERIALIZED VIEW IF EXISTS mv_finance_cashflow_daily              CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_metrics_daily             CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_revenue_daily                       CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_metrics_professional_daily CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_service_metrics_daily     CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_no_show_appointments                CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_customer_top_services_daily         CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_customer_service_rank_daily         CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_relatorio_agendamentos               CASCADE;

-- ─── 2. ALTER das colunas ─────────────────────────────────────────────────────

ALTER TABLE transactions
  ALTER COLUMN amount TYPE NUMERIC(19,2) USING (amount::NUMERIC / 100);

ALTER TABLE recurring_transactions
  ALTER COLUMN amount TYPE NUMERIC(19,2) USING (amount::NUMERIC / 100);

ALTER TABLE services
  ALTER COLUMN price TYPE NUMERIC(19,2) USING (price::NUMERIC / 100);

ALTER TABLE appointment_items
  DROP CONSTRAINT IF EXISTS ck_appointment_items_total_matches_gross_minus_discount,
  DROP CONSTRAINT IF EXISTS ck_appointment_items_discount_not_greater_than_gross;

ALTER TABLE appointment_items
  ALTER COLUMN unit_price      TYPE NUMERIC(19,2) USING (unit_price::NUMERIC / 100),
  ALTER COLUMN gross_amount    TYPE NUMERIC(19,2) USING (gross_amount::NUMERIC / 100),
  ALTER COLUMN discount_amount TYPE NUMERIC(19,2) USING (discount_amount::NUMERIC / 100),
  ALTER COLUMN total_price     TYPE NUMERIC(19,2) USING (total_price::NUMERIC / 100);

ALTER TABLE appointment_items
  ADD CONSTRAINT ck_appointment_items_discount_not_greater_than_gross
    CHECK (discount_amount <= gross_amount),
  ADD CONSTRAINT ck_appointment_items_total_matches_gross_minus_discount
    CHECK (total_price = gross_amount - discount_amount);

-- ─── 3. Recriar views sem ::bigint em colunas monetárias ─────────────────────

-- mv_finance_cashflow_daily
CREATE MATERIALIZED VIEW mv_finance_cashflow_daily AS
SELECT
  tenant_id,
  (date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
  SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE 0 END)       AS total_income,
  SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END)       AS total_expenses,
  SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE -amount END) AS balance
FROM transactions
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

-- mv_dashboard_service_metrics_daily
CREATE MATERIALIZED VIEW mv_dashboard_service_metrics_daily AS
WITH appointments_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    ai.service_id,
    a.date AS metric_date,
    COUNT(DISTINCT a.id)::int AS total_appointments,
    COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Concluido')::int AS completed_appointments,
    COUNT(DISTINCT a.id) FILTER (WHERE a.status = 'Cancelado')::int AS canceled_appointments,
    COALESCE(SUM(ai.total_price) FILTER (WHERE a.status = 'Concluido'), 0) AS revenue
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

-- mv_no_show_appointments
CREATE MATERIALIZED VIEW mv_no_show_appointments AS
SELECT
  a.tenant_id,
  a.id AS appointment_id,
  a.date AS metric_date,
  a.date,
  a.start_time,
  a.end_time,
  a.created_at,
  a.notes,
  a.status,
  a.client_id,
  c.name AS client_name,
  c.phone AS client_phone,
  c.email AS client_email,
  a.professional_id,
  p.name AS professional_name,
  COUNT(ai.id)::int AS total_services,
  COALESCE(SUM(ai.total_price), 0) AS total_price,
  COALESCE(string_agg(DISTINCT s.id::text, '||' ORDER BY s.id::text), '') AS service_ids,
  COALESCE(string_agg(DISTINCT s.name, '||' ORDER BY s.name), '') AS service_names
FROM appointments a
LEFT JOIN clients c
  ON c.id = a.client_id
 AND c.tenant_id = a.tenant_id
LEFT JOIN professionals p
  ON p.id = a.professional_id
 AND p.tenant_id = a.tenant_id
LEFT JOIN appointment_items ai
  ON ai.appointment_id = a.id
 AND ai.tenant_id = a.tenant_id
LEFT JOIN services s
  ON s.id = ai.service_id
 AND s.tenant_id = ai.tenant_id
WHERE a.status = 'Nao compareceu'
GROUP BY
  a.tenant_id, a.id, a.date, a.start_time, a.end_time, a.created_at,
  a.notes, a.status, a.client_id, c.name, c.phone, c.email,
  a.professional_id, p.name
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_no_show_appointments_tenant_appointment
  ON mv_no_show_appointments (tenant_id, appointment_id);
CREATE INDEX idx_mv_no_show_appointments_tenant_date
  ON mv_no_show_appointments (tenant_id, metric_date DESC, start_time DESC, created_at DESC);
CREATE INDEX idx_mv_no_show_appointments_tenant_professional_date
  ON mv_no_show_appointments (tenant_id, professional_id, metric_date DESC);
CREATE INDEX idx_mv_no_show_appointments_tenant_client
  ON mv_no_show_appointments (tenant_id, client_id);

-- mv_customer_top_services_daily
CREATE MATERIALIZED VIEW mv_customer_top_services_daily AS
SELECT
  a.tenant_id,
  a.client_id,
  ai.service_id,
  a.professional_id,
  a.date AS metric_date,
  COUNT(DISTINCT a.id)::int AS completed_appointments,
  COALESCE(SUM(ai.quantity), 0)::int AS completed_services,
  COALESCE(SUM(ai.total_price), 0) AS revenue_total,
  MAX(a.date) AS last_appointment_date
FROM appointments a
JOIN appointment_items ai
  ON ai.tenant_id = a.tenant_id
 AND ai.appointment_id = a.id
WHERE a.status = 'Concluido'
GROUP BY a.tenant_id, a.client_id, ai.service_id, a.professional_id, a.date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_customer_top_services_daily_tenant_client_service_professional_date
  ON mv_customer_top_services_daily (tenant_id, client_id, service_id, professional_id, metric_date);
CREATE INDEX idx_mv_customer_top_services_daily_tenant_client_date
  ON mv_customer_top_services_daily (tenant_id, client_id, metric_date DESC);
CREATE INDEX idx_mv_customer_top_services_daily_tenant_client_service_date
  ON mv_customer_top_services_daily (tenant_id, client_id, service_id, metric_date DESC);
CREATE INDEX idx_mv_customer_top_services_daily_tenant_client_service_professional_date
  ON mv_customer_top_services_daily (tenant_id, client_id, service_id, professional_id, metric_date DESC);

-- mv_customer_service_rank_daily
CREATE MATERIALIZED VIEW mv_customer_service_rank_daily AS
SELECT
  a.tenant_id,
  a.client_id,
  a.date AS metric_date,
  COUNT(DISTINCT a.id)::int AS completed_appointments,
  COALESCE(SUM(ai.quantity), 0)::int AS completed_services,
  COALESCE(SUM(ai.total_price), 0) AS revenue_total,
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

-- mv_relatorio_agendamentos
CREATE MATERIALIZED VIEW mv_relatorio_agendamentos AS
WITH appointment_base AS (
  SELECT
    a.tenant_id,
    a.id AS appointment_id,
    a.date AS data_agendamento,
    a.start_time AS horario,
    a.end_time,
    a.created_at,
    a.client_id,
    COALESCE(c.name, 'Sem cliente') AS cliente,
    a.professional_id,
    COALESCE(p.name, 'Sem profissional') AS profissional,
    a.status,
    COALESCE(SUM(ai.total_price), 0) AS valor,
    COALESCE(array_agg(DISTINCT s.id) FILTER (WHERE s.id IS NOT NULL), ARRAY[]::uuid[]) AS service_ids,
    COALESCE(string_agg(DISTINCT s.name, '||' ORDER BY s.name), 'Sem servico') AS servico
  FROM appointments a
  LEFT JOIN clients c
    ON c.id = a.client_id
   AND c.tenant_id = a.tenant_id
  LEFT JOIN professionals p
    ON p.id = a.professional_id
   AND p.tenant_id = a.tenant_id
  LEFT JOIN appointment_items ai
    ON ai.appointment_id = a.id
   AND ai.tenant_id = a.tenant_id
  LEFT JOIN services s
    ON s.id = ai.service_id
   AND s.tenant_id = ai.tenant_id
  GROUP BY
    a.tenant_id, a.id, a.date, a.start_time, a.end_time, a.created_at,
    a.client_id, c.name, a.professional_id, p.name, a.status
),
appointment_create_audit AS (
  SELECT
    ae.tenant_id,
    ae.entity_id,
    ae.actor_user_id,
    ae.metadata_json,
    ROW_NUMBER() OVER (
      PARTITION BY ae.tenant_id, ae.entity_id
      ORDER BY ae.created_at DESC, ae.id DESC
    ) AS rn
  FROM audit_events ae
  WHERE ae.entity_type = 'APPOINTMENT'
    AND ae.action IN ('APPOINTMENT_CREATE', 'APPOINTMENT_CREATE_WITH_CONFLICT_OVERRIDE')
    AND ae.status = 'SUCCESS'
),
abandonment_daily AS (
  SELECT
    m.tenant_id,
    m.metric_date,
    COALESCE(SUM(m.sessions_count), 0)::int AS total_abandonado
  FROM mv_appointment_booking_abandon_daily_stage m
  GROUP BY m.tenant_id, m.metric_date
),
decorated AS (
  SELECT
    b.*,
    CASE
      WHEN UPPER(COALESCE(a.metadata_json::jsonb ->> 'origin', '')) = 'INTERNAL_MANUAL' THEN 'MANUAL'
      WHEN a.actor_user_id IS NOT NULL THEN 'MANUAL'
      WHEN a.entity_id IS NOT NULL THEN 'SISTEMA'
      ELSE 'NAO_IDENTIFICADA'
    END AS origem,
    COALESCE(d.total_abandonado, 0)::int AS total_abandonado,
    LEAD(b.horario) OVER (
      PARTITION BY b.tenant_id, b.professional_id, b.data_agendamento
      ORDER BY b.horario::time, b.created_at, b.appointment_id
    ) AS proximo_horario
  FROM appointment_base b
  LEFT JOIN appointment_create_audit a
    ON a.tenant_id = b.tenant_id
   AND a.entity_id = b.appointment_id::text
   AND a.rn = 1
  LEFT JOIN abandonment_daily d
    ON d.tenant_id = b.tenant_id
   AND d.metric_date = b.data_agendamento
)
SELECT
  tenant_id, appointment_id, data_agendamento, horario, end_time,
  client_id, cliente, service_ids, servico, professional_id, profissional,
  status, origem, valor, created_at,
  1::int AS total_agendamentos,
  CASE WHEN status = 'Confirmado' THEN 1 ELSE 0 END::int AS total_confirmados,
  CASE WHEN status = 'Pendente' THEN 1 ELSE 0 END::int AS total_pendentes,
  CASE WHEN status = 'Cancelado' THEN 1 ELSE 0 END::int AS total_cancelados,
  CASE WHEN status = 'Nao compareceu' THEN 1 ELSE 0 END::int AS total_no_show,
  CASE WHEN status IN ('Confirmado', 'Em andamento', 'Concluido') THEN 100.0 ELSE 0.0 END::double precision AS taxa_ocupacao,
  CASE WHEN status = 'Cancelado' THEN 100.0 ELSE 0.0 END::double precision AS taxa_cancelamento,
  CASE WHEN status = 'Nao compareceu' THEN 100.0 ELSE 0.0 END::double precision AS taxa_no_show,
  CASE
    WHEN proximo_horario IS NULL THEN FALSE
    WHEN (proximo_horario::time - end_time::time) >= interval '30 minutes' THEN TRUE
    ELSE FALSE
  END AS flag_horario_vago,
  CASE WHEN status = 'Pendente' THEN TRUE ELSE FALSE END AS flag_nao_confirmado,
  CASE WHEN total_abandonado > 0 THEN TRUE ELSE FALSE END AS flag_abandono_fluxo
FROM decorated
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_relatorio_agendamentos_tenant_appointment
  ON mv_relatorio_agendamentos (tenant_id, appointment_id);
CREATE INDEX idx_mv_relatorio_agendamentos_data
  ON mv_relatorio_agendamentos (tenant_id, data_agendamento DESC);
CREATE INDEX idx_mv_relatorio_agendamentos_profissional
  ON mv_relatorio_agendamentos (tenant_id, professional_id, data_agendamento DESC);
CREATE INDEX idx_mv_relatorio_agendamentos_status
  ON mv_relatorio_agendamentos (tenant_id, status, data_agendamento DESC);
CREATE INDEX idx_mv_relatorio_agendamentos_servicos
  ON mv_relatorio_agendamentos USING GIN (service_ids);

-- ─── 4. Refresh state ─────────────────────────────────────────────────────────

INSERT INTO report_materialized_view_refresh_state (view_name, refreshed_at)
VALUES
  ('mv_dashboard_metrics_daily',              CURRENT_TIMESTAMP),
  ('mv_revenue_daily',                        CURRENT_TIMESTAMP),
  ('mv_dashboard_metrics_professional_daily', CURRENT_TIMESTAMP),
  ('mv_dashboard_service_metrics_daily',      CURRENT_TIMESTAMP),
  ('mv_finance_cashflow_daily',               CURRENT_TIMESTAMP),
  ('mv_no_show_appointments',                 CURRENT_TIMESTAMP),
  ('mv_customer_top_services_daily',          CURRENT_TIMESTAMP),
  ('mv_customer_service_rank_daily',          CURRENT_TIMESTAMP),
  ('mv_relatorio_agendamentos',               CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO UPDATE
  SET refreshed_at = EXCLUDED.refreshed_at;
