-- Area da cliente, ajustes depois da revisao de 20/11/2026 (PLANO-AREA-DA-CLIENTE.md).
--
-- ATENCAO: email_jobs JA EXISTE EM PRODUCAO. Antes de publicar, conferir no banco de producao (so leitura) que o unico
-- CHECK de email_type e o chk_email_jobs_type com PASSWORD_RESET e EMAIL_VERIFICATION, e que nao ha gatilho na tabela.
-- O resto da migration so mexe em tabelas da V185/V186 (ainda nao publicadas).

-- E-mails da conta da cliente (confirmar e-mail, senha nova, avisos), com texto proprio e nao o do login do salao.
ALTER TABLE email_jobs DROP CONSTRAINT IF EXISTS chk_email_jobs_type;
ALTER TABLE email_jobs ADD CONSTRAINT chk_email_jobs_type
  CHECK (email_type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION', 'CLIENTE_AVISO'));

-- O servico pode exigir um exame (texto do que a cliente deve enviar). Vale o exame enviado nos ultimos 12 meses.
ALTER TABLE exigencias_do_servico ADD COLUMN IF NOT EXISTS exame_exigido VARCHAR(200);

-- Registro das acoes da conta (entrar, trocar senha, CPF, e-mail, excluir...). A conta nao e de um salao, entao isto
-- nao vai para a auditoria dos salões. Fica 1 ano (rotina diaria).
CREATE TABLE IF NOT EXISTS eventos_da_conta (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conta_id UUID NOT NULL REFERENCES contas_de_cliente(id),
  tipo VARCHAR(40) NOT NULL,
  detalhe VARCHAR(200),
  ip VARCHAR(60),
  navegador VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_eventos_da_conta ON eventos_da_conta (conta_id, created_at DESC);
