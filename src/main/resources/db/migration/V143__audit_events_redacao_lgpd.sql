-- Redacao de dado pessoal do cliente na trilha de auditoria (LGPD, anonimizacao).
--
-- audit_events e append-only: o gatilho recusa todo UPDATE e so abre excecao para o DELETE da purga
-- de retencao. Esta migration abre UMA excecao, estreita, so para a anonimizacao do cliente — e
-- NAO altera deny_append_only_mutation(), que e compartilhada por mais 4 tabelas (retencao, termos,
-- aceites, ciclo de vida dos termos). audit_events ganha uma funcao propria.
--
-- O UPDATE so passa se TUDO isto for verdade:
--   * a sessao ligou app.audit_redaction (set_config local a transacao, feito so pela anonimizacao);
--   * o evento e de CLIENT e ainda nao tinha sido redigido (redacted_at nulo -> preenchido);
--   * NENHUMA coluna alem de before_json, after_json, metadata_json e redacted_at mudou — em
--     particular event_hash, prev_event_hash, created_at, ator e acao ficam identicos. O hash NAO e
--     recalculado: ele continua cobrindo o conteudo original e a cadeia segue ligada.
ALTER TABLE audit_events ADD COLUMN IF NOT EXISTS redacted_at TIMESTAMP WITH TIME ZONE;

CREATE OR REPLACE FUNCTION deny_audit_events_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE'
     AND current_setting('app.audit_retention_purge', true) = 'on' THEN
    RETURN OLD;
  END IF;

  IF TG_OP = 'UPDATE'
     AND current_setting('app.audit_redaction', true) = 'on'
     AND OLD.entity_type = 'CLIENT'
     AND OLD.redacted_at IS NULL
     AND NEW.redacted_at IS NOT NULL
     AND (to_jsonb(NEW) - ARRAY['before_json', 'after_json', 'metadata_json', 'redacted_at'])
         = (to_jsonb(OLD) - ARRAY['before_json', 'after_json', 'metadata_json', 'redacted_at']) THEN
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'append-only table: mutation blocked';
END;
$$;

DROP TRIGGER IF EXISTS trg_deny_audit_events_mutation ON audit_events;
CREATE TRIGGER trg_deny_audit_events_mutation
  BEFORE DELETE OR UPDATE ON audit_events
  FOR EACH ROW EXECUTE FUNCTION deny_audit_events_mutation();
