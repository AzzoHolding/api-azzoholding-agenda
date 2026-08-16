DROP MATERIALIZED VIEW IF EXISTS mv_no_show_appointments;

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
  COALESCE(SUM(ai.total_price), 0)::bigint AS total_price,
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
  a.tenant_id,
  a.id,
  a.date,
  a.start_time,
  a.end_time,
  a.created_at,
  a.notes,
  a.status,
  a.client_id,
  c.name,
  c.phone,
  c.email,
  a.professional_id,
  p.name;

CREATE UNIQUE INDEX uq_mv_no_show_appointments_tenant_appointment
  ON mv_no_show_appointments (tenant_id, appointment_id);

CREATE INDEX idx_mv_no_show_appointments_tenant_date
  ON mv_no_show_appointments (tenant_id, metric_date DESC, start_time DESC, created_at DESC);

CREATE INDEX idx_mv_no_show_appointments_tenant_professional_date
  ON mv_no_show_appointments (tenant_id, professional_id, metric_date DESC);

CREATE INDEX idx_mv_no_show_appointments_tenant_client
  ON mv_no_show_appointments (tenant_id, client_id);

REFRESH MATERIALIZED VIEW mv_no_show_appointments;

INSERT INTO report_materialized_view_refresh_state (view_name, refreshed_at)
VALUES ('mv_no_show_appointments', CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO UPDATE
SET refreshed_at = EXCLUDED.refreshed_at;
