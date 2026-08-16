ALTER TABLE tenant_whatsapp_config
  ADD COLUMN IF NOT EXISTS meta_business_id VARCHAR(100),
  ADD COLUMN IF NOT EXISTS display_phone_number VARCHAR(30),
  ADD COLUMN IF NOT EXISTS whatsapp_onboarding_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
  ADD COLUMN IF NOT EXISTS whatsapp_token_source VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN IF NOT EXISTS embedded_signup_completed_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS embedded_signup_last_error VARCHAR(500);

UPDATE tenant_whatsapp_config
SET whatsapp_onboarding_status = CASE
      WHEN COALESCE(NULLIF(TRIM(whatsapp_phone_number_id), ''), NULL) IS NOT NULL
       AND COALESCE(NULLIF(TRIM(whatsapp_access_token_enc), ''), NULL) IS NOT NULL
      THEN 'CONNECTED'
      ELSE 'NOT_STARTED'
    END,
    whatsapp_token_source = 'MANUAL',
    embedded_signup_completed_at = CASE
      WHEN COALESCE(NULLIF(TRIM(whatsapp_phone_number_id), ''), NULL) IS NOT NULL
       AND COALESCE(NULLIF(TRIM(whatsapp_access_token_enc), ''), NULL) IS NOT NULL
      THEN COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
      ELSE embedded_signup_completed_at
    END
WHERE whatsapp_onboarding_status IS NULL
   OR whatsapp_onboarding_status NOT IN ('NOT_STARTED', 'PENDING_EXCHANGE', 'CONNECTED', 'FAILED', 'DISCONNECTED')
   OR whatsapp_token_source IS NULL
   OR whatsapp_token_source = '';
