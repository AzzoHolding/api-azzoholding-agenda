ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS lgpd_contact_email VARCHAR(255),
  ADD COLUMN IF NOT EXISTS lgpd_contact_channel VARCHAR(100),
  ADD COLUMN IF NOT EXISTS lgpd_contact_response_sla_days INTEGER DEFAULT 15;
