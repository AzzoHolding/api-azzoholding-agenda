ALTER TABLE clients
    ADD COLUMN IF NOT EXISTS loyalty_points INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS tenant_loyalty_settings (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    ativo BOOLEAN NOT NULL DEFAULT FALSE,
    pontos_por_real NUMERIC(10,4) NOT NULL DEFAULT 1,
    produtos_contam BOOLEAN NOT NULL DEFAULT FALSE,
    validade_dias INT,
    pontos_por_resgate_real NUMERIC(10,4) NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
