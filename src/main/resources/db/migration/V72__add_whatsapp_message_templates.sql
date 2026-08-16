ALTER TABLE tenant_whatsapp_config
  ADD COLUMN IF NOT EXISTS confirmation_message_template TEXT,
  ADD COLUMN IF NOT EXISTS cancellation_message_template TEXT,
  ADD COLUMN IF NOT EXISTS reminder_message_template     TEXT;
