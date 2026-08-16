DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM information_schema.tables
     WHERE table_schema = 'azzo_app'
       AND table_name = 'password_reset_email_jobs'
  ) AND NOT EXISTS (
    SELECT 1
      FROM information_schema.tables
     WHERE table_schema = 'azzo_app'
       AND table_name = 'email_jobs'
  ) THEN
    ALTER TABLE password_reset_email_jobs RENAME TO email_jobs;
  END IF;
END $$;

ALTER TABLE IF EXISTS email_jobs
  ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE IF EXISTS email_jobs
  ADD COLUMN IF NOT EXISTS related_entity_type VARCHAR(100),
  ADD COLUMN IF NOT EXISTS related_entity_id UUID,
  ADD COLUMN IF NOT EXISTS email_type VARCHAR(50),
  ADD COLUMN IF NOT EXISTS payload_json TEXT;

UPDATE email_jobs
   SET email_type = 'PASSWORD_RESET'
 WHERE email_type IS NULL;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = 'azzo_app'
       AND table_name = 'email_jobs'
       AND column_name = 'password_reset_token_id'
  ) THEN
    EXECUTE $sql$
      UPDATE email_jobs
         SET related_entity_type = 'PASSWORD_RESET_TOKEN'
       WHERE related_entity_type IS NULL
    $sql$;

    EXECUTE $sql$
      UPDATE email_jobs
         SET related_entity_id = password_reset_token_id
       WHERE related_entity_id IS NULL
         AND password_reset_token_id IS NOT NULL
    $sql$;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = 'azzo_app'
       AND table_name = 'email_jobs'
       AND column_name = 'reset_url'
  ) AND EXISTS (
    SELECT 1
      FROM information_schema.columns
     WHERE table_schema = 'azzo_app'
       AND table_name = 'email_jobs'
       AND column_name = 'password_reset_token_id'
  ) THEN
    EXECUTE $sql$
      UPDATE email_jobs
         SET payload_json = jsonb_build_object(
             'resetUrl', reset_url,
             'passwordResetTokenId', password_reset_token_id
           )::text
       WHERE payload_json IS NULL
    $sql$;
  END IF;
END $$;

ALTER TABLE IF EXISTS email_jobs
  ALTER COLUMN email_type SET NOT NULL,
  ALTER COLUMN payload_json SET NOT NULL;

ALTER TABLE IF EXISTS email_jobs
  DROP CONSTRAINT IF EXISTS chk_password_reset_email_jobs_status;

ALTER TABLE IF EXISTS email_jobs
  ADD CONSTRAINT chk_email_jobs_status
    CHECK (status IN ('NEW', 'PROCESSED', 'FAILED'));

ALTER TABLE IF EXISTS email_jobs
  DROP CONSTRAINT IF EXISTS uq_password_reset_email_jobs_token,
  DROP CONSTRAINT IF EXISTS fk_password_reset_email_jobs_token;

ALTER TABLE IF EXISTS email_jobs
  DROP COLUMN IF EXISTS password_reset_token_id,
  DROP COLUMN IF EXISTS reset_url;

ALTER TABLE IF EXISTS email_jobs
  DROP CONSTRAINT IF EXISTS chk_email_jobs_type;

ALTER TABLE IF EXISTS email_jobs
  ADD CONSTRAINT chk_email_jobs_type
    CHECK (email_type IN ('PASSWORD_RESET'));

DROP INDEX IF EXISTS idx_password_reset_email_jobs_status_created_at;
DROP INDEX IF EXISTS idx_password_reset_email_jobs_tenant_created_at;

CREATE INDEX IF NOT EXISTS idx_email_jobs_status_created_at
  ON email_jobs (status, created_at);

CREATE INDEX IF NOT EXISTS idx_email_jobs_tenant_created_at
  ON email_jobs (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_jobs_type_status_created_at
  ON email_jobs (email_type, status, created_at);
