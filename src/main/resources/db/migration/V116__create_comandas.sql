ALTER TABLE appointment_deposits
    ADD COLUMN IF NOT EXISTS used_in_comanda_id UUID;

CREATE TABLE IF NOT EXISTS comandas (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
    client_id UUID REFERENCES clients(id) ON DELETE SET NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'ABERTA'
        CHECK (status IN ('ABERTA', 'FECHADA', 'CANCELADA')),
    subtotal NUMERIC(10,2) NOT NULL DEFAULT 0,
    desconto NUMERIC(10,2) NOT NULL DEFAULT 0,
    desconto_motivo TEXT,
    gorjeta NUMERIC(10,2) NOT NULL DEFAULT 0,
    gorjeta_professional_id UUID REFERENCES professionals(id) ON DELETE SET NULL,
    total NUMERIC(10,2) NOT NULL DEFAULT 0,
    aberta_por UUID,
    fechada_por UUID,
    cancel_motivo TEXT,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comandas_tenant_status ON comandas (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_comandas_appointment ON comandas (appointment_id);

CREATE TABLE IF NOT EXISTS comanda_itens (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    comanda_id UUID NOT NULL REFERENCES comandas(id) ON DELETE CASCADE,
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('SERVICO', 'PRODUTO', 'PACOTE')),
    referencia_id UUID NOT NULL,
    descricao TEXT NOT NULL,
    professional_id UUID REFERENCES professionals(id) ON DELETE SET NULL,
    quantidade NUMERIC(10,3) NOT NULL DEFAULT 1,
    preco_unitario NUMERIC(10,2) NOT NULL,
    total NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comanda_itens_comanda ON comanda_itens (comanda_id);

CREATE TABLE IF NOT EXISTS comanda_pagamentos (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    comanda_id UUID NOT NULL REFERENCES comandas(id) ON DELETE CASCADE,
    meio VARCHAR(24) NOT NULL CHECK (meio IN
        ('DINHEIRO', 'PIX_ASAAS', 'CARTAO_CREDITO_EXTERNO', 'CARTAO_DEBITO_EXTERNO', 'CREDITO_SINAL')),
    valor NUMERIC(10,2) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'CONFIRMADO', 'ESTORNADO')),
    asaas_payment_id VARCHAR(64),
    pix_payload TEXT,
    appointment_deposit_id UUID REFERENCES appointment_deposits(id) ON DELETE SET NULL,
    registrado_por UUID,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_comanda_pagamentos_comanda ON comanda_pagamentos (comanda_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_comanda_pagamentos_asaas_payment_id
    ON comanda_pagamentos (asaas_payment_id) WHERE asaas_payment_id IS NOT NULL;
