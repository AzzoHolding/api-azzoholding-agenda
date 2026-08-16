CREATE TABLE IF NOT EXISTS agendamento_configuracao (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
  permitir_agendamento_manual_com_conflito BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE agendamento_configuracao IS
  'Configuracoes operacionais da agenda interna por tenant.';

COMMENT ON COLUMN agendamento_configuracao.permitir_agendamento_manual_com_conflito IS
  'Quando ativo, a agenda interna pode confirmar agendamentos manuais em horario ja ocupado apos confirmacao explicita.';

CREATE INDEX IF NOT EXISTS idx_agendamento_configuracao_tenant
  ON agendamento_configuracao (tenant_id);
