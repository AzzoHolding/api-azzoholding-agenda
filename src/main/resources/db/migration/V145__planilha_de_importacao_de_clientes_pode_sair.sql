-- LGPD (analise de 2026-10-13): a planilha de importacao de clientes (nome, telefone, CPF da base
-- inteira) ficava no storage para sempre. Agora ela sai quando o job termina, e o job guarda
-- arquivo_storage_key = NULL. So afrouxa a coluna: nenhuma linha muda, nenhum CHECK ou gatilho.
ALTER TABLE importacao_cliente_job ALTER COLUMN arquivo_storage_key DROP NOT NULL;
