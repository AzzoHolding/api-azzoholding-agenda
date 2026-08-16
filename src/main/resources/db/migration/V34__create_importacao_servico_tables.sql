CREATE TABLE IF NOT EXISTS importacao_servico_job (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome_arquivo VARCHAR(255) NOT NULL,
  arquivo_storage_key VARCHAR(500) NOT NULL,
  arquivo_hash_sha256 VARCHAR(128) NOT NULL,
  status VARCHAR(30) NOT NULL,
  modo_importacao VARCHAR(30) NOT NULL,
  dry_run BOOLEAN NOT NULL DEFAULT FALSE,
  linhas_recebidas INTEGER NOT NULL DEFAULT 0,
  linhas_processadas INTEGER NOT NULL DEFAULT 0,
  linhas_sucesso INTEGER NOT NULL DEFAULT 0,
  linhas_erro INTEGER NOT NULL DEFAULT 0,
  mensagem_resumo VARCHAR(1000),
  error_message VARCHAR(4000),
  requested_by UUID,
  started_at TIMESTAMP,
  finished_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_importacao_servico_job_tenant_created
  ON importacao_servico_job (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_importacao_servico_job_tenant_status
  ON importacao_servico_job (tenant_id, status);

CREATE TABLE IF NOT EXISTS importacao_servico_erro_linha (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  job_id UUID NOT NULL REFERENCES importacao_servico_job(id) ON DELETE CASCADE,
  linha INTEGER NOT NULL,
  coluna VARCHAR(100),
  codigo VARCHAR(100),
  mensagem VARCHAR(4000) NOT NULL,
  valor_original VARCHAR(2000),
  created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_importacao_servico_erro_linha_job
  ON importacao_servico_erro_linha (job_id, linha);
