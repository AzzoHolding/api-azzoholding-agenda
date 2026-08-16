-- Inicializa horarios padrao (9h-19h seg-sex, 9h-17h sab, 9h-13h dom desabilitado)
-- para tenants que possuem tenant_operational_settings mas ainda nao tem registros
-- na nova tabela tenant_business_hours.
-- O campo business_hours_json e mantido como deprecated para compatibilidade retroativa.

INSERT INTO tenant_business_hours (tenant_id, day_of_week, open_time, close_time, enabled)
SELECT
  tos.tenant_id,
  d.day_of_week,
  d.open_time::TIME,
  d.close_time::TIME,
  d.enabled
FROM tenant_operational_settings tos
CROSS JOIN (VALUES
  ('MONDAY',    '09:00', '19:00', true),
  ('TUESDAY',   '09:00', '19:00', true),
  ('WEDNESDAY', '09:00', '19:00', true),
  ('THURSDAY',  '09:00', '19:00', true),
  ('FRIDAY',    '09:00', '19:00', true),
  ('SATURDAY',  '09:00', '17:00', true),
  ('SUNDAY',    '09:00', '13:00', false)
) AS d(day_of_week, open_time, close_time, enabled)
WHERE NOT EXISTS (
  SELECT 1 FROM tenant_business_hours tbh
  WHERE tbh.tenant_id = tos.tenant_id
)
ON CONFLICT (tenant_id, day_of_week) DO NOTHING;
