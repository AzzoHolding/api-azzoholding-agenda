-- PDV vende produto do estoque (plano PLANO-FIDELIDADE-PACOTES.md, etapa 4): preco de venda no item.
--
-- Opcional: insumo (sem preco) nao aparece para venda. Com preco, o PDV mostra o produto e a comanda
-- cobra esse preco. Conferido em producao em 14/11/2026 (so leitura): itens_estoque sem gatilho nem
-- CHECK; a view materializada mv_estoque_resumo_tenant depende da tabela, mas acrescentar coluna nao
-- a afeta.
ALTER TABLE itens_estoque ADD COLUMN IF NOT EXISTS preco_venda NUMERIC(12,2);
