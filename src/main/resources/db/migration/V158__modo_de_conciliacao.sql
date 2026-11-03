-- Como o salao concilia: lancamento a lancamento (padrao), por dia ou por mes. Decisao do dono em
-- 02/11/2026. O padrao mantem o que ja existe em todo salao.
-- Conferido em producao em 02/11/2026: sem gatilho, 1 CHECK de outra coluna (desconto), 1 linha.
ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS reconciliation_mode VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';

ALTER TABLE tenant_operational_settings
  ADD CONSTRAINT ck_tenant_operational_settings_reconciliation_mode
  CHECK (reconciliation_mode IN ('INDIVIDUAL', 'DAILY', 'MONTHLY'));
