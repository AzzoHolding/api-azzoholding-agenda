CREATE TABLE IF NOT EXISTS whatsapp_message_log (
  id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  tenant_id           UUID          NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  appointment_id      UUID,
  event_type          VARCHAR(60)   NOT NULL,
  destination_phone   VARCHAR(30)   NOT NULL,
  message_text        TEXT,
  provider_message_id VARCHAR(120),
  status              VARCHAR(20)   NOT NULL DEFAULT 'SENT',
  error_message       TEXT,
  sent_at             TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_wamsglog_tenant_sent ON whatsapp_message_log (tenant_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_wamsglog_appointment  ON whatsapp_message_log (appointment_id) WHERE appointment_id IS NOT NULL;
