-- Pacotes (14/11/2026, plano PLANO-FIDELIDADE-PACOTES.md, etapas 5 e 6).
--
-- client_package_balances.valor_sessao: o valor de cada sessao, gravado na venda e proporcional ao
--   preco de tabela de cada servico do pacote (decisao do dono: um corte de R$ 80 vale mais que uma
--   escova de R$ 50 na comissao). Vazio nas compras antigas: elas seguem a divisao igual.
-- client_package_balances.expira_em: quando as sessoes vencem (vazio = nao vencem).
-- service_packages.validade_dias: validade opcional do pacote, em dias a partir da compra.
--
-- Conferido em producao em 14/11/2026 (so leitura): as duas tabelas sem gatilho, sem CHECK e sem
-- view dependente; 1 pacote e nenhum saldo. So colunas novas e opcionais.
ALTER TABLE client_package_balances
  ADD COLUMN IF NOT EXISTS valor_sessao NUMERIC(12,2),
  ADD COLUMN IF NOT EXISTS expira_em TIMESTAMP WITH TIME ZONE;

ALTER TABLE service_packages
  ADD COLUMN IF NOT EXISTS validade_dias INTEGER;
