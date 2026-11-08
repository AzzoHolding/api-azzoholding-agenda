-- Papel da API do salao (api_agenda) SEM superusuario e SEM posse das tabelas (08/11/2026).
--
-- Rodar como o dono (azzo), passando a senha por variavel — ela nunca entra no repositorio:
--   psql -U azzo -d azzo_negocios -v ON_ERROR_STOP=1 -v senha="$API_DB_PASSWORD" -f papel-da-api.sql
-- Pode rodar de novo (troca a senha e reaplica os direitos).
--
-- O que muda: a API le e grava dados, mas nao cria/altera/apaga tabela, nao faz TRUNCATE, nao desliga
-- gatilho (append-only da auditoria) e nao usa session_replication_role. As migrations continuam com
-- o dono (FLYWAY_DB_USERNAME). O REFRESH das views de relatorio passa pela funcao da V165.

SELECT format('CREATE ROLE azzo_api LOGIN PASSWORD %L', :'senha')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'azzo_api') \gexec
SELECT format('ALTER ROLE azzo_api LOGIN PASSWORD %L', :'senha') \gexec
ALTER ROLE azzo_api NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS INHERIT;

GRANT CONNECT, TEMPORARY ON DATABASE azzo_negocios TO azzo_api;
GRANT USAGE ON SCHEMA azzo_app, public TO azzo_api;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA azzo_app TO azzo_api;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA azzo_app TO azzo_api;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA azzo_app TO azzo_api;

-- O historico do Flyway e so do dono.
REVOKE INSERT, UPDATE, DELETE ON azzo_app.flyway_schema_history FROM azzo_api;

-- Tabelas, sequencias e funcoes que as proximas migrations criarem (como azzo) ja nascem com o direito.
ALTER DEFAULT PRIVILEGES FOR ROLE azzo IN SCHEMA azzo_app
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO azzo_api;
ALTER DEFAULT PRIVILEGES FOR ROLE azzo IN SCHEMA azzo_app
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO azzo_api;
ALTER DEFAULT PRIVILEGES FOR ROLE azzo IN SCHEMA azzo_app
  GRANT EXECUTE ON FUNCTIONS TO azzo_api;

SELECT rolname, rolsuper, rolcreaterole, rolcreatedb, rolbypassrls FROM pg_roles WHERE rolname = 'azzo_api';
