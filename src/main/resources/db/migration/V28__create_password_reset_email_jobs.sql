CREATE TABLE IF NOT EXISTS password_reset_email_jobs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  password_reset_token_id UUID NOT NULL,
  recipient_email VARCHAR(255) NOT NULL,
  recipient_name VARCHAR(255),
  reset_url TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  error_message TEXT,
  provider_status VARCHAR(50),
  from_email VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMP WITH TIME ZONE,
  CONSTRAINT chk_password_reset_email_jobs_status
    CHECK (status IN ('NEW', 'PROCESSED', 'FAILED')),
  CONSTRAINT uq_password_reset_email_jobs_token UNIQUE (password_reset_token_id),
  CONSTRAINT fk_password_reset_email_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_password_reset_email_jobs_token FOREIGN KEY (password_reset_token_id) REFERENCES password_reset_tokens (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_reset_email_jobs_status_created_at
  ON password_reset_email_jobs (status, created_at);

CREATE INDEX IF NOT EXISTS idx_password_reset_email_jobs_tenant_created_at
  ON password_reset_email_jobs (tenant_id, created_at DESC);
