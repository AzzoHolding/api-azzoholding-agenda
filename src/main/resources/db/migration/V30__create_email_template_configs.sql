CREATE TABLE email_template_configs (
  id UUID PRIMARY KEY,
  tenant_id UUID NULL,
  template_type VARCHAR(50) NOT NULL,
  from_email VARCHAR(255) NULL,
  from_name VARCHAR(255) NULL,
  reply_to VARCHAR(255) NULL,
  subject_template TEXT NOT NULL,
  html_template TEXT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by UUID NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_email_template_configs_global_type
  ON email_template_configs (template_type)
  WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX uq_email_template_configs_tenant_type
  ON email_template_configs (tenant_id, template_type)
  WHERE tenant_id IS NOT NULL;

CREATE INDEX idx_email_template_configs_active
  ON email_template_configs (is_active, updated_at DESC);
