-- RESET DEV MIGRATION
-- Objetivo: limpar objetos do schema public e reiniciar historico do Flyway.
-- Observacao: migration destrutiva para ambiente local/homologacao controlado.

DO $$
DECLARE
  r RECORD;
BEGIN
  -- Materialized views
  FOR r IN (
    SELECT schemaname, matviewname
    FROM pg_matviews
    WHERE schemaname = 'azzo_app'
  ) LOOP
    EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS %I.%I CASCADE', r.schemaname, r.matviewname);
  END LOOP;

  -- Views
  FOR r IN (
    SELECT table_schema, table_name
    FROM information_schema.views
    WHERE table_schema = 'azzo_app'
  ) LOOP
    EXECUTE format('DROP VIEW IF EXISTS %I.%I CASCADE', r.table_schema, r.table_name);
  END LOOP;

  -- Tables (preserva flyway_schema_history para nao quebrar controle de migracao)
  FOR r IN (
    SELECT table_schema, table_name
    FROM information_schema.tables
    WHERE table_schema = 'azzo_app'
      AND table_type = 'BASE TABLE'
      AND table_name <> 'flyway_schema_history'
  ) LOOP
    EXECUTE format('DROP TABLE IF EXISTS %I.%I CASCADE', r.table_schema, r.table_name);
  END LOOP;

  -- Sequences
  FOR r IN (
    SELECT sequence_schema, sequence_name
    FROM information_schema.sequences
    WHERE sequence_schema = 'azzo_app'
  ) LOOP
    EXECUTE format('DROP SEQUENCE IF EXISTS %I.%I CASCADE', r.sequence_schema, r.sequence_name);
  END LOOP;

  -- Nao remove functions/types para evitar conflitos com objetos de extensoes
  -- (ex.: uuid-ossp) que pertencem ao schema public.
END $$;


