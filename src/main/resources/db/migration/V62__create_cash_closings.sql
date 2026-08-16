CREATE TABLE cash_closings (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  business_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  opened_at TIMESTAMPTZ NOT NULL,
  opened_by UUID NULL REFERENCES users(id),
  opening_notes TEXT NULL,
  closed_at TIMESTAMPTZ NULL,
  closed_by UUID NULL REFERENCES users(id),
  closing_notes TEXT NULL,
  expected_totals_json TEXT NOT NULL DEFAULT '{}',
  counted_totals_json TEXT NOT NULL DEFAULT '{}',
  difference_totals_json TEXT NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_cash_closings_tenant_business_date UNIQUE (tenant_id, business_date),
  CONSTRAINT ck_cash_closings_status CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_cash_closings_tenant_business_date
  ON cash_closings (tenant_id, business_date DESC);

CREATE INDEX idx_cash_closings_tenant_status
  ON cash_closings (tenant_id, status);

COMMENT ON TABLE cash_closings IS 'Fechamentos diarios de caixa por tenant com snapshot esperado/declarado por forma de pagamento.';
COMMENT ON COLUMN cash_closings.expected_totals_json IS 'JSON serializado com totais liquidos esperados por metodo de pagamento, em centavos.';
COMMENT ON COLUMN cash_closings.counted_totals_json IS 'JSON serializado com totais declarados/contados por metodo de pagamento, em centavos.';
COMMENT ON COLUMN cash_closings.difference_totals_json IS 'JSON serializado com diferencas entre contado e esperado por metodo de pagamento, em centavos.';
