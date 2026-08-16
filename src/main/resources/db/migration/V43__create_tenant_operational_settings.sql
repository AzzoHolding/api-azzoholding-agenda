CREATE TABLE IF NOT EXISTS tenant_operational_settings (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
  sms_notifications BOOLEAN NOT NULL DEFAULT TRUE,
  whatsapp_notifications BOOLEAN NOT NULL DEFAULT TRUE,
  reminder_hours INTEGER NOT NULL DEFAULT 24,
  reactivation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  reactivation_respect_business_hours BOOLEAN NOT NULL DEFAULT TRUE,
  reactivation_send_window_start VARCHAR(5) NOT NULL DEFAULT '09:00',
  reactivation_send_window_end VARCHAR(5) NOT NULL DEFAULT '19:00',
  business_hours_json TEXT NOT NULL DEFAULT '[]',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_operational_settings_updated_at
  ON tenant_operational_settings (updated_at);
