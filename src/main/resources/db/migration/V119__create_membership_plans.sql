CREATE TABLE IF NOT EXISTS membership_plans (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    nome TEXT NOT NULL,
    descricao TEXT,
    preco_mensal NUMERIC(10,2) NOT NULL,
    cumulativo BOOLEAN NOT NULL DEFAULT FALSE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_membership_plans_tenant ON membership_plans (tenant_id);

CREATE TABLE IF NOT EXISTS membership_plan_benefits (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES membership_plans(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    quantidade_mensal INT NOT NULL CHECK (quantidade_mensal > 0)
);

CREATE INDEX IF NOT EXISTS idx_membership_plan_benefits_plan ON membership_plan_benefits (plan_id);

CREATE TABLE IF NOT EXISTS client_memberships (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    plan_id UUID REFERENCES membership_plans(id) ON DELETE SET NULL,
    plan_nome TEXT NOT NULL,
    preco_mensal NUMERIC(10,2) NOT NULL,
    cumulativo BOOLEAN NOT NULL DEFAULT FALSE,
    asaas_subscription_id VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'ATIVA'
        CHECK (status IN ('ATIVA', 'INADIMPLENTE', 'SUSPENSA', 'CANCELADA')),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_memberships_client ON client_memberships (client_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_client_memberships_asaas_subscription_id
    ON client_memberships (asaas_subscription_id) WHERE asaas_subscription_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_client_memberships_status_period_end
    ON client_memberships (status, cancel_at_period_end, period_end);

CREATE TABLE IF NOT EXISTS client_membership_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    membership_id UUID NOT NULL REFERENCES client_memberships(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    service_nome TEXT NOT NULL,
    quantidade_mensal INT NOT NULL,
    usadas_no_periodo INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_client_membership_balances_membership ON client_membership_balances (membership_id);
