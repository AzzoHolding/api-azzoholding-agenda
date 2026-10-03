-- Confirmacao de e-mail no cadastro: a conta nova so entra depois de abrir o link enviado ao
-- e-mail informado. SO o cadastro liga a marca; usuarios existentes e os criados por convite
-- (profissional, perfil de acesso) ficam com FALSE e nao sao afetados.
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS email_confirmation_pending BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_email_verification_tokens_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_user_active
  ON email_verification_tokens (user_id, expires_at)
  WHERE used_at IS NULL;

ALTER TABLE email_jobs DROP CONSTRAINT IF EXISTS chk_email_jobs_type;
ALTER TABLE email_jobs
  ADD CONSTRAINT chk_email_jobs_type CHECK (email_type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'));
