-- Como o salao concilia: lancamento a lancamento (padrao), por dia ou por mes. Decisao do dono em
-- 02/11/2026. O padrao mantem o que ja existe em todo salao.
-- Conferido em producao em 02/11/2026: sem gatilho, 1 CHECK de outra coluna (desconto), 1 linha.
ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS reconciliation_mode VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';

ALTER TABLE tenant_operational_settings
  ADD CONSTRAINT ck_tenant_operational_settings_reconciliation_mode
  CHECK (reconciliation_mode IN ('INDIVIDUAL', 'DAILY', 'MONTHLY'));

-- Cada conciliacao finalizada guarda o intervalo: a seguinte comeca onde esta terminou.
CREATE TABLE reconciliation_periods (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  mode VARCHAR(20) NOT NULL,
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  reconciled_count INT NOT NULL,
  divergent_count INT NOT NULL,
  created_by UUID NULL REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_reconciliation_periods_mode CHECK (mode IN ('DAILY', 'MONTHLY'))
);

CREATE INDEX idx_reconciliation_periods_tenant_ends_at
  ON reconciliation_periods (tenant_id, ends_at DESC);
