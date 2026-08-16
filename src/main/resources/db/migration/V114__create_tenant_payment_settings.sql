CREATE TABLE IF NOT EXISTS tenant_payment_settings (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL DEFAULT 'ASAAS',
    ambiente VARCHAR(16) NOT NULL DEFAULT 'SANDBOX',
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    api_key_enc TEXT,
    webhook_token VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_tenant_payment_settings_webhook_token
    ON tenant_payment_settings (webhook_token);
