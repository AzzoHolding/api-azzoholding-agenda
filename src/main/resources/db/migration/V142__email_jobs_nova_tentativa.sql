-- Nova tentativa com espera na fila de e-mail. Aditiva: so duas colunas (uma com DEFAULT).
-- Job em espera de nova tentativa continua NEW (com next_attempt_at no futuro); so vira FAILED
-- quando as tentativas acabam. Linhas existentes ficam com attempts = 0 e next_attempt_at nulo.
ALTER TABLE email_jobs
  ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;
