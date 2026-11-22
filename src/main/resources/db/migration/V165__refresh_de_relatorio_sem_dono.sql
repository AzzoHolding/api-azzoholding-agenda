-- A API deixa de conectar como superusuario e dono das tabelas (08/11/2026): com esse poder, qualquer
-- gatilho (append-only da auditoria) podia ser desligado. O unico comando que a API rodava e que
-- exige ser DONO e o REFRESH das views de relatorio. Esta funcao faz so isso, com o dono, e so para
-- views que existem neste schema — nome de tabela ou qualquer outra coisa e recusado.
CREATE OR REPLACE FUNCTION atualizar_view_de_relatorio(nome text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = azzo_app, pg_temp AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_matviews WHERE schemaname = 'azzo_app' AND matviewname = nome) THEN
    RAISE EXCEPTION 'view de relatorio desconhecida: %', nome;
  END IF;
  EXECUTE format('REFRESH MATERIALIZED VIEW CONCURRENTLY azzo_app.%I', nome);
END;
$$;

REVOKE ALL ON FUNCTION atualizar_view_de_relatorio(text) FROM PUBLIC;

-- O papel da API e criado fora do Flyway (a senha nao entra no repositorio): scripts/banco no
-- servidor. Se ele ja existir, recebe o direito aqui tambem.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'azzo_api') THEN
    EXECUTE 'GRANT EXECUTE ON FUNCTION atualizar_view_de_relatorio(text) TO azzo_api';
  END IF;
END;
$$;
