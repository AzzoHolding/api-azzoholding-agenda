ALTER TABLE tenant_whatsapp_config
  ADD COLUMN IF NOT EXISTS whatsapp_usage_profile VARCHAR(20) NOT NULL DEFAULT 'COMPLETE';

COMMENT ON COLUMN tenant_whatsapp_config.whatsapp_usage_profile IS
  'Perfil de uso do WhatsApp: REACTIVE_ONLY (apenas responde), NOTIFICATIONS (lembretes/confirmacoes), COMPLETE (tudo)';
