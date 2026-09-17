-- Pacote e assinatura passam a DESCONTAR o uso (analise de 2026-09-16, achado A5). Decisao do
-- usuario: a RECEPCAO escolhe, na comanda, se o servico sai do pacote ou da assinatura do cliente.
--
-- O item coberto fica com preco zero na conta (o cliente ja pagou pelo pacote/plano) e guarda:
--   cobertura_tipo          PACOTE | ASSINATURA
--   cobertura_saldo_id      o saldo de onde a sessao sai (client_package_balances ou
--                           client_membership_balances — por isso sem FK)
--   valor_cobertura         o valor da sessao dentro do que o cliente pagou (preco pago / sessoes):
--                           e a base da comissao do profissional, que nao trabalha de graca
--   preco_antes_cobertura   o preco que o item tinha, para voltar se a cobertura for retirada
--
-- A sessao so e descontada quando a comanda FECHA (e volta no estorno): comanda abandonada ou
-- cancelada nao consome o pacote de ninguem.

ALTER TABLE comanda_itens ADD COLUMN IF NOT EXISTS cobertura_tipo VARCHAR(20);
ALTER TABLE comanda_itens ADD COLUMN IF NOT EXISTS cobertura_saldo_id UUID;
ALTER TABLE comanda_itens ADD COLUMN IF NOT EXISTS valor_cobertura NUMERIC(12, 2);
ALTER TABLE comanda_itens ADD COLUMN IF NOT EXISTS preco_antes_cobertura NUMERIC(12, 2);
