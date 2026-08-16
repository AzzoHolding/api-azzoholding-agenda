-- LGPD: adiciona TTL de 90 dias para whatsapp_message_log
-- Mensagens contêm destination_phone e message_text: dados pessoais não devem ser retidos indefinidamente
-- sent_at confirmado na DDL V73__create_whatsapp_message_log.sql (linha 11): TIMESTAMP NOT NULL DEFAULT now()
ALTER TABLE whatsapp_message_log
  ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP NOT NULL DEFAULT (NOW() + INTERVAL '90 days');

-- Retroativamente define expires_at para registros existentes com base em sent_at
UPDATE whatsapp_message_log
SET expires_at = sent_at + INTERVAL '90 days'
WHERE expires_at > (NOW() + INTERVAL '89 days');

CREATE INDEX IF NOT EXISTS idx_whatsapp_message_log_expires_at
  ON whatsapp_message_log (expires_at);
