CREATE TABLE IF NOT EXISTS tenant_telegram_config (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    telegram_bot_token_enc TEXT NOT NULL DEFAULT '',
    telegram_bot_username VARCHAR(255),
    telegram_webhook_secret_token_enc TEXT,
    telegram_webhook_secret_token_hash VARCHAR(128),
    telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_telegram_config_enabled
    ON tenant_telegram_config (telegram_enabled);

CREATE INDEX IF NOT EXISTS idx_tenant_telegram_config_secret_hash
    ON tenant_telegram_config (telegram_webhook_secret_token_hash);
