-- Trilha de auditoria solida: hash completo (v2), cadeia selada por um unico selador, ancora do
-- expurgo, registro das conferencias e bloqueio de TRUNCATE.
--
-- Por que mudou (conferido em producao em 07/11/2026):
--   * 52 bifurcacoes (71 eventos a mais): duas transacoes do mesmo salao liam o "ultimo evento" ao
--     mesmo tempo e encadeavam no mesmo anterior. O elo era calculado na hora da escrita, sem trava.
--   * O hash (v1) deixava de fora ator, papel, IP, navegador, erro e chave de idempotencia.
--   * Ninguem recalculava a cadeia.
--
-- Como fica (eventos novos, hash_version = 2):
--   1. A escrita grava o evento com event_hash = hash de TODO o conteudo. Os JSON (before, after,
--      metadata) entram pelo digest de cada um, guardado em payload_digests: a redacao de LGPD
--      troca o JSON, mas o digest original fica, e o resto do evento continua conferivel.
--   2. Um selador unico (pg_try_advisory_xact_lock) liga os eventos ja confirmados, por salao, na
--      ordem: chain_seq (1, 2, 3...), prev_event_hash (chain_hash do anterior) e chain_hash.
--      O indice unico (tenant_id, chain_seq) impede bifurcacao no proprio banco.
--   3. O gatilho aceita UPDATE so para selar: as colunas de selo saem de nulo para valor, e nenhuma
--      outra muda, com app.audit_seal ligado na transacao.
--
-- Os eventos antigos (hash_version = 1) ficam como estao; a conferencia os trata como legado.

ALTER TABLE audit_events
  ADD COLUMN IF NOT EXISTS hash_version SMALLINT NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS payload_digests VARCHAR(200),
  ADD COLUMN IF NOT EXISTS chain_seq BIGINT,
  ADD COLUMN IF NOT EXISTS chain_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS sealed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE audit_events
  ADD CONSTRAINT ck_audit_events_hash_version CHECK (hash_version IN (1, 2)),
  ADD CONSTRAINT ck_audit_events_selo_completo CHECK (
    (chain_seq IS NULL AND chain_hash IS NULL AND sealed_at IS NULL)
    OR (chain_seq IS NOT NULL AND chain_hash IS NOT NULL AND sealed_at IS NOT NULL)),
  ADD CONSTRAINT ck_audit_events_v2_tem_digests CHECK (hash_version = 1 OR payload_digests IS NOT NULL);

CREATE UNIQUE INDEX IF NOT EXISTS ux_audit_events_tenant_chain_seq
  ON audit_events (tenant_id, chain_seq) WHERE chain_seq IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_audit_events_nao_selados
  ON audit_events (created_at, id) WHERE hash_version = 2 AND chain_seq IS NULL;

CREATE OR REPLACE FUNCTION deny_audit_events_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE'
     AND current_setting('app.audit_retention_purge', true) = 'on' THEN
    RETURN OLD;
  END IF;

  -- Redacao de LGPD (V143): so os JSON e redacted_at mudam.
  IF TG_OP = 'UPDATE'
     AND current_setting('app.audit_redaction', true) = 'on'
     AND OLD.entity_type = 'CLIENT'
     AND OLD.redacted_at IS NULL
     AND NEW.redacted_at IS NOT NULL
     AND (to_jsonb(NEW) - ARRAY['before_json', 'after_json', 'metadata_json', 'redacted_at'])
         = (to_jsonb(OLD) - ARRAY['before_json', 'after_json', 'metadata_json', 'redacted_at']) THEN
    RETURN NEW;
  END IF;

  -- Selo da cadeia: uma vez, de nulo para valor, so nas colunas de selo de um evento v2.
  IF TG_OP = 'UPDATE'
     AND current_setting('app.audit_seal', true) = 'on'
     AND OLD.hash_version = 2
     AND OLD.chain_seq IS NULL
     AND OLD.chain_hash IS NULL
     AND OLD.prev_event_hash IS NULL
     AND NEW.chain_seq IS NOT NULL
     AND NEW.chain_hash IS NOT NULL
     AND NEW.sealed_at IS NOT NULL
     AND (to_jsonb(NEW) - ARRAY['chain_seq', 'chain_hash', 'prev_event_hash', 'sealed_at'])
         = (to_jsonb(OLD) - ARRAY['chain_seq', 'chain_hash', 'prev_event_hash', 'sealed_at']) THEN
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'append-only table: mutation blocked';
END;
$$;

-- O expurgo guarda onde a cadeia continua: o ultimo elo removido. Sem isso, o primeiro evento que
-- sobra aponta para um hash que nao existe mais, e a conferencia nao distingue expurgo de adulteracao.
ALTER TABLE audit_retention_events
  ADD COLUMN IF NOT EXISTS chain_anchor_hash VARCHAR(128),
  ADD COLUMN IF NOT EXISTS chain_anchor_seq BIGINT;

-- Cada conferencia da cadeia deixa registro (append-only, como as demais tabelas de evidencia).
CREATE TABLE IF NOT EXISTS audit_chain_verifications (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
  status VARCHAR(30) NOT NULL,
  checked_events BIGINT NOT NULL,
  sealed_events BIGINT NOT NULL,
  legacy_events BIGINT NOT NULL,
  legacy_forks BIGINT NOT NULL,
  redacted_events BIGINT NOT NULL,
  pending_seal BIGINT NOT NULL,
  failures BIGINT NOT NULL,
  first_failure_event_id UUID,
  first_failure_reason VARCHAR(300),
  last_chain_seq BIGINT,
  last_chain_hash VARCHAR(64),
  executed_by VARCHAR(80) NOT NULL,
  started_at TIMESTAMP WITH TIME ZONE NOT NULL,
  finished_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_audit_chain_verifications_status
    CHECK (status IN ('INTEGRA', 'INTEGRA_COM_RESSALVAS', 'VIOLADA'))
);

CREATE INDEX IF NOT EXISTS ix_audit_chain_verifications_tenant
  ON audit_chain_verifications (tenant_id, finished_at DESC);

DROP TRIGGER IF EXISTS trg_deny_audit_chain_verifications_mutation ON audit_chain_verifications;
CREATE TRIGGER trg_deny_audit_chain_verifications_mutation
  BEFORE UPDATE OR DELETE ON audit_chain_verifications
  FOR EACH ROW EXECUTE FUNCTION deny_append_only_mutation();

-- TRUNCATE nao passa pelos gatilhos de linha: apagava a trilha inteira sem nenhum bloqueio.
CREATE OR REPLACE FUNCTION deny_audit_truncate() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'append-only table: TRUNCATE blocked on %', TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS trg_deny_audit_events_truncate ON audit_events;
CREATE TRIGGER trg_deny_audit_events_truncate
  BEFORE TRUNCATE ON audit_events
  FOR EACH STATEMENT EXECUTE FUNCTION deny_audit_truncate();

DROP TRIGGER IF EXISTS trg_deny_audit_retention_events_truncate ON audit_retention_events;
CREATE TRIGGER trg_deny_audit_retention_events_truncate
  BEFORE TRUNCATE ON audit_retention_events
  FOR EACH STATEMENT EXECUTE FUNCTION deny_audit_truncate();

DROP TRIGGER IF EXISTS trg_deny_audit_chain_verifications_truncate ON audit_chain_verifications;
CREATE TRIGGER trg_deny_audit_chain_verifications_truncate
  BEFORE TRUNCATE ON audit_chain_verifications
  FOR EACH STATEMENT EXECUTE FUNCTION deny_audit_truncate();

REVOKE TRUNCATE ON audit_events, audit_retention_events, audit_chain_verifications FROM PUBLIC;
