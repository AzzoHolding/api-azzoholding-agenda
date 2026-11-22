-- Notas da Receita (07/11/2026, pedido do dono): os documentos que o Ambiente de Dados Nacional
-- (ADN) distribui para o CNPJ do salao — NFS-e em que ele e prestador, tomador ou intermediario, e
-- os eventos (cancelamento). Inclui as notas emitidas FORA do Azzo (portal, contador, outro sistema),
-- que entram na apuracao mensal e na exportacao do contador.
--
-- Tabelas novas (sem gatilho nem CHECK de tabela existente para conferir). O XML guardado e o oficial,
-- cifrado pelo EncryptionService; os campos soltos sao lidos dele para listar e somar sem decifrar.

CREATE TABLE IF NOT EXISTS nfse_documentos_da_receita (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  ambiente VARCHAR(20) NOT NULL,
  nsu BIGINT NOT NULL,
  chave_acesso VARCHAR(60),
  tipo_documento VARCHAR(20) NOT NULL,
  tipo_evento VARCHAR(80),
  papel VARCHAR(20),
  numero_nfse VARCHAR(20),
  competencia DATE,
  emitida_em TIMESTAMPTZ,
  valor_servicos NUMERIC(15, 2),
  valor_iss NUMERIC(15, 2),
  iss_retido BOOLEAN NOT NULL DEFAULT FALSE,
  prestador_documento VARCHAR(20),
  prestador_nome VARCHAR(255),
  tomador_documento VARCHAR(20),
  tomador_nome VARCHAR(255),
  descricao VARCHAR(500),
  cancelada BOOLEAN NOT NULL DEFAULT FALSE,
  chave_do_evento VARCHAR(60),
  xml_enc BYTEA,
  recebido_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_doc_receita_ambiente CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO')),
  CONSTRAINT ck_nfse_doc_receita_papel CHECK (papel IS NULL OR papel IN ('PRESTADOR', 'TOMADOR', 'INTERMEDIARIO', 'OUTRO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_doc_receita_nsu
  ON nfse_documentos_da_receita (tenant_id, ambiente, nsu);

CREATE INDEX IF NOT EXISTS idx_nfse_doc_receita_chave
  ON nfse_documentos_da_receita (tenant_id, chave_acesso);

CREATE INDEX IF NOT EXISTS idx_nfse_doc_receita_competencia
  ON nfse_documentos_da_receita (tenant_id, ambiente, competencia);

-- Onde a ultima busca parou, por ambiente: a proxima pede so o que veio depois.
CREATE TABLE IF NOT EXISTS nfse_receita_cursores (
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  ambiente VARCHAR(20) NOT NULL,
  ultimo_nsu BIGINT NOT NULL DEFAULT 0,
  ultima_busca_em TIMESTAMPTZ,
  PRIMARY KEY (tenant_id, ambiente),
  CONSTRAINT ck_nfse_receita_cursor_ambiente CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO'))
);
