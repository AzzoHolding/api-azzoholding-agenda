CREATE TABLE tenant_business_hours (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  day_of_week VARCHAR(10) NOT NULL,
  open_time TIME,
  close_time TIME,
  enabled BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP NOT NULL DEFAULT now(),
  updated_at TIMESTAMP NOT NULL DEFAULT now(),
  CONSTRAINT fk_bh_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT uq_tenant_day UNIQUE (tenant_id, day_of_week)
);

CREATE INDEX idx_bh_tenant_id ON tenant_business_hours (tenant_id);
