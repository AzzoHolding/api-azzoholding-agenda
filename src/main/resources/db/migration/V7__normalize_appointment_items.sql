-- Fase 5 de normalizacao: appointments ganham itens relacionais.
-- service_id e total_price em appointments permanecem temporariamente como snapshot de compatibilidade.

CREATE TABLE IF NOT EXISTS appointment_items (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
  service_id UUID NOT NULL,
  quantity INTEGER NOT NULL DEFAULT 1,
  unit_price BIGINT NOT NULL,
  total_price BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_appointment_items_service_tenant
    FOREIGN KEY (tenant_id, service_id) REFERENCES services(tenant_id, id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_appointment_items_appointment_id ON appointment_items (appointment_id);
CREATE INDEX IF NOT EXISTS idx_appointment_items_service_id ON appointment_items (service_id);

INSERT INTO appointment_items (
  id,
  tenant_id,
  appointment_id,
  service_id,
  quantity,
  unit_price,
  total_price,
  created_at
)
SELECT
   public.uuid_generate_v4(),
  a.tenant_id,
  a.id,
  a.service_id,
  1,
  CASE WHEN a.total_price > 0 THEN a.total_price ELSE COALESCE(s.price, 0) END,
  CASE WHEN a.total_price > 0 THEN a.total_price ELSE COALESCE(s.price, 0) END,
  NOW()
FROM appointments a
LEFT JOIN services s
  ON s.id = a.service_id
 AND s.tenant_id = a.tenant_id
WHERE NOT EXISTS (
  SELECT 1 FROM appointment_items ai WHERE ai.appointment_id = a.id
);
