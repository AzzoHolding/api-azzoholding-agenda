ALTER TABLE clients ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_clients_anonymized_at
    ON clients (tenant_id, anonymized_at)
    WHERE anonymized_at IS NOT NULL;
