-- LGPD: a auditoria de CLIENT guardava o retrato inteiro do cliente (nome, e-mail, telefone,
-- endereco, nascimento, notas). Anonimizar ou excluir o cliente nao tocava nisso, e a trilha dura
-- 365 dias: o dado pessoal ficava ate um ano. O codigo novo nao grava mais esses valores; esta
-- migration redige os eventos que JA existem.
--
-- `redacted_at` marca o evento redigido. O `event_hash` NAO e recalculado de proposito: ele segue
-- sendo o hash do conteudo original, para a cadeia (`prev_event_hash`) continuar ligada. Quem
-- verificar a cadeia trata o evento com `redacted_at` como "conteudo removido por LGPD".
ALTER TABLE audit_events
  ADD COLUMN IF NOT EXISTS redacted_at TIMESTAMPTZ;

CREATE OR REPLACE FUNCTION pg_temp.redigir_cliente(j text) RETURNS text
LANGUAGE sql AS $$
  SELECT CASE
    WHEN j IS NULL OR btrim(j) = '' THEN j
    WHEN jsonb_typeof(j::jsonb) <> 'object' THEN j
    ELSE COALESCE(
      (SELECT jsonb_object_agg(
                t.k,
                CASE
                  WHEN t.k = ANY (ARRAY['name','email','phone','avatar','birthDate','notes','zipCode',
                                        'street','number','complement','neighborhood','city','state',
                                        'cpfCnpj'])
                       AND jsonb_typeof(t.v) <> 'null'
                  THEN to_jsonb('[REDIGIDO]'::text)
                  ELSE t.v
                END)
         FROM jsonb_each(j::jsonb) AS t(k, v))::text,
      j)
  END
$$;

UPDATE audit_events
   SET before_json   = pg_temp.redigir_cliente(before_json),
       after_json    = pg_temp.redigir_cliente(after_json),
       metadata_json = pg_temp.redigir_cliente(metadata_json),
       redacted_at   = now()
 WHERE entity_type = 'CLIENT'
   AND action IN ('CLIENT_CREATE', 'CLIENT_UPDATE', 'CLIENT_DELETE')
   AND redacted_at IS NULL;
