ALTER TABLE services
    ADD COLUMN IF NOT EXISTS requires_deposit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS deposit_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS deposit_value NUMERIC(10,2);

ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS asaas_customer_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS appointment_deposits (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    asaas_payment_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    amount_cents BIGINT NOT NULL,
    pix_payload TEXT,
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_appointment_deposits_asaas_payment_id
    ON appointment_deposits (asaas_payment_id);

CREATE INDEX IF NOT EXISTS idx_appointment_deposits_appointment_id
    ON appointment_deposits (appointment_id);

CREATE INDEX IF NOT EXISTS idx_appointment_deposits_status_expires
    ON appointment_deposits (status, expires_at);
