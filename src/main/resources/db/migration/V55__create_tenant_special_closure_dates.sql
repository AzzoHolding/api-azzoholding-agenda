CREATE TABLE IF NOT EXISTS tenant_special_closure_dates (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  closure_date DATE NOT NULL,
  reason VARCHAR(160),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tenant_special_closure_dates_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT uk_tenant_special_closure_dates_tenant_date
    UNIQUE (tenant_id, closure_date)
);

CREATE INDEX IF NOT EXISTS idx_tenant_special_closure_dates_tenant
  ON tenant_special_closure_dates (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_special_closure_dates_tenant_date
  ON tenant_special_closure_dates (tenant_id, closure_date);
