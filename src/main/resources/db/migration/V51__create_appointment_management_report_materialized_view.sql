DROP MATERIALIZED VIEW IF EXISTS mv_relatorio_agendamentos;

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
    COALESCE(SUM(ai.total_price), 0)::bigint AS valor,
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
    a.tenant_id,
    a.id,
    a.date,
    a.start_time,
    a.end_time,
    a.created_at,
    a.client_id,
    c.name,
    a.professional_id,
    p.name,
    a.status
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
  tenant_id,
  appointment_id,
  data_agendamento,
  horario,
  end_time,
  client_id,
  cliente,
  service_ids,
  servico,
  professional_id,
  profissional,
  status,
  origem,
  valor,
  created_at,
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
FROM decorated;

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

REFRESH MATERIALIZED VIEW mv_relatorio_agendamentos;

INSERT INTO report_materialized_view_refresh_state (view_name, refreshed_at)
VALUES ('mv_relatorio_agendamentos', CURRENT_TIMESTAMP)
ON CONFLICT (view_name) DO UPDATE
SET refreshed_at = EXCLUDED.refreshed_at;
