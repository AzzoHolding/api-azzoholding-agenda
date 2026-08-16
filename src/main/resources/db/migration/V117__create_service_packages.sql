CREATE TABLE IF NOT EXISTS service_packages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    nome TEXT NOT NULL,
    descricao TEXT,
    preco NUMERIC(10,2) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_service_packages_tenant ON service_packages (tenant_id);

CREATE TABLE IF NOT EXISTS service_package_items (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    package_id UUID NOT NULL REFERENCES service_packages(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    sessoes INT NOT NULL CHECK (sessoes > 0)
);

CREATE INDEX IF NOT EXISTS idx_service_package_items_package ON service_package_items (package_id);

CREATE TABLE IF NOT EXISTS client_package_purchases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    package_id UUID REFERENCES service_packages(id) ON DELETE SET NULL,
    package_nome TEXT NOT NULL,
    preco_pago NUMERIC(10,2) NOT NULL,
    comanda_id UUID REFERENCES comandas(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_package_purchases_client ON client_package_purchases (client_id);

CREATE TABLE IF NOT EXISTS client_package_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    purchase_id UUID NOT NULL REFERENCES client_package_purchases(id) ON DELETE CASCADE,
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    service_nome TEXT NOT NULL,
    sessoes_totais INT NOT NULL,
    sessoes_usadas INT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_client_package_balances_purchase ON client_package_balances (purchase_id);
