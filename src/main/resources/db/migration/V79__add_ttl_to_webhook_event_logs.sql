-- LGPD: adiciona TTL de 30 dias para webhook_event_logs
-- Reduz retenção de dados financeiros de webhook (minimização de dados - art. 6, III LGPD)
-- created_at confirmado na DDL baseline (V1__baseline_unified.sql, linha 448): TIMESTAMPTZ NOT NULL DEFAULT NOW()
ALTER TABLE webhook_event_logs
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 days');

-- Retroativamente define expires_at para registros existentes com base em created_at
UPDATE webhook_event_logs
SET expires_at = created_at + INTERVAL '30 days'
WHERE expires_at > (NOW() + INTERVAL '29 days');

CREATE INDEX IF NOT EXISTS idx_webhook_event_logs_expires_at
  ON webhook_event_logs (expires_at);
