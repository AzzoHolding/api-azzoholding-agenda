DROP MATERIALIZED VIEW IF EXISTS mv_whatsapp_booking_reactivation_daily;

CREATE MATERIALIZED VIEW mv_whatsapp_booking_reactivation_daily AS
WITH abandoned AS (
  SELECT
    tenant_id,
    abandoned_at::date AS metric_date,
    COUNT(*)::int AS abandoned_count,
    COUNT(*) FILTER (WHERE last_stage = 'SERVICE_SELECTION')::int AS stopped_at_service_selection,
    COUNT(*) FILTER (WHERE last_stage = 'PROFESSIONAL_SELECTION')::int AS stopped_at_professional_selection,
    COUNT(*) FILTER (WHERE last_stage = 'TIME_SELECTION')::int AS stopped_at_time_selection,
    COUNT(*) FILTER (WHERE last_stage = 'FINAL_REVIEW')::int AS stopped_at_final_review
  FROM whatsapp_booking_reactivation_cycles
  WHERE abandoned_at IS NOT NULL
    AND abandoned_at <= CURRENT_TIMESTAMP
  GROUP BY tenant_id, abandoned_at::date
),
reactivated AS (
  SELECT
    tenant_id,
    reactivated_at::date AS metric_date,
    COUNT(*)::int AS reactivated_count
  FROM whatsapp_booking_reactivation_cycles
  WHERE reactivated_at IS NOT NULL
    AND reactivated_at <= CURRENT_TIMESTAMP
  GROUP BY tenant_id, reactivated_at::date
),
converted AS (
  SELECT
    tenant_id,
    converted_at::date AS metric_date,
    COUNT(*)::int AS converted_count
  FROM whatsapp_booking_reactivation_cycles
  WHERE converted_at IS NOT NULL
    AND converted_at <= CURRENT_TIMESTAMP
  GROUP BY tenant_id, converted_at::date
),
keys AS (
  SELECT tenant_id, metric_date FROM abandoned
  UNION
  SELECT tenant_id, metric_date FROM reactivated
  UNION
  SELECT tenant_id, metric_date FROM converted
)
SELECT
  k.tenant_id,
  k.metric_date,
  COALESCE(a.abandoned_count, 0)::int AS abandoned_count,
  COALESCE(r.reactivated_count, 0)::int AS reactivated_count,
  COALESCE(c.converted_count, 0)::int AS converted_count,
  COALESCE(a.stopped_at_service_selection, 0)::int AS stopped_at_service_selection,
  COALESCE(a.stopped_at_professional_selection, 0)::int AS stopped_at_professional_selection,
  COALESCE(a.stopped_at_time_selection, 0)::int AS stopped_at_time_selection,
  COALESCE(a.stopped_at_final_review, 0)::int AS stopped_at_final_review
FROM keys k
LEFT JOIN abandoned a
  ON a.tenant_id = k.tenant_id
 AND a.metric_date = k.metric_date
LEFT JOIN reactivated r
  ON r.tenant_id = k.tenant_id
 AND r.metric_date = k.metric_date
LEFT JOIN converted c
  ON c.tenant_id = k.tenant_id
 AND c.metric_date = k.metric_date;

CREATE UNIQUE INDEX uq_mv_whatsapp_booking_reactivation_daily_tenant_date
  ON mv_whatsapp_booking_reactivation_daily (tenant_id, metric_date);

CREATE INDEX idx_mv_whatsapp_booking_reactivation_daily_metric_date
  ON mv_whatsapp_booking_reactivation_daily (metric_date);

CREATE INDEX idx_mv_whatsapp_booking_reactivation_daily_tenant_date
  ON mv_whatsapp_booking_reactivation_daily (tenant_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_whatsapp_booking_reactivation_daily;

INSERT INTO report_materialized_view_refresh_state (view_name, refreshed_at)
VALUES ('mv_whatsapp_booking_reactivation_daily', CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO UPDATE
SET refreshed_at = EXCLUDED.refreshed_at;
