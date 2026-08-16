ALTER TABLE nfse_certificate_unlock_sessions
  ADD COLUMN IF NOT EXISTS password_enc TEXT;
