-- Template pertence a WABA, e nao ao app: os templates do Azzo nao existem na conta do cliente.
-- No modelo de provedor, quem cria e o Azzo -- ele tem `whatsapp_business_management` sobre a WABA
-- do salao e pode criar por API, sem o dono saber que "template" existe.
--
-- Criar NAO e aprovar. A Meta analisa, e ate o status virar APPROVED o template nao envia nada.
-- Sem guardar isso, o salao fica com mensagens que nunca chegam e ninguem sabe por que -- que e
-- exatamente o que aconteceu em producao em 2026-09-22 por outro motivo.
CREATE TABLE IF NOT EXISTS whatsapp_templates (
  id                UUID PRIMARY KEY,
  tenant_id         UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  -- TESTE, CONFIRMACAO, CANCELAMENTO, LEMBRETE
  finalidade        VARCHAR(30)  NOT NULL,
  nome              VARCHAR(120) NOT NULL,
  idioma            VARCHAR(12)  NOT NULL DEFAULT 'pt_BR',
  meta_template_id  VARCHAR(60),
  -- PENDING, APPROVED, REJECTED, PAUSED, DISABLED -- espelha o vocabulario da Meta.
  status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
  motivo_recusa     TEXT,
  -- As variaveis do corpo, em ordem: e o que liga {{1}} ao nome do cliente na hora do envio.
  variaveis         TEXT,
  corpo             TEXT,
  criado_em         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  atualizado_em     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  -- Um template por finalidade por salao: dois "confirmacao" seria ambiguidade na hora de enviar.
  CONSTRAINT uq_whatsapp_templates_tenant_finalidade UNIQUE (tenant_id, finalidade)
);

CREATE INDEX IF NOT EXISTS ix_whatsapp_templates_tenant ON whatsapp_templates (tenant_id);
-- O monitoramento procura os que ainda nao foram decididos.
CREATE INDEX IF NOT EXISTS ix_whatsapp_templates_pendentes ON whatsapp_templates (status) WHERE status = 'PENDING';
