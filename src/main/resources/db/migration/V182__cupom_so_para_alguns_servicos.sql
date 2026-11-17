-- Cupom so para alguns servicos (pedido do dono, 17/11/2026; PLANO-CUPONS-VALES.md).
-- Sem linha aqui, o cupom vale para a conta toda (como antes). Com linhas, o desconto incide so sobre os itens
-- desses servicos; o valor minimo continua sendo da conta (sem o vale-presente vendido).
-- O lote do gerador guarda a lista uma vez (lote_de_cupom_servicos), e os codigos dele seguem a lista do lote.
-- Tabelas novas: nada de gatilho ou CHECK em tabela existente.

CREATE TABLE IF NOT EXISTS cupom_servicos (
  cupom_id UUID NOT NULL REFERENCES cupons(id) ON DELETE CASCADE,
  service_id UUID NOT NULL,
  PRIMARY KEY (cupom_id, service_id)
);

CREATE TABLE IF NOT EXISTS lote_de_cupom_servicos (
  lote_id UUID NOT NULL REFERENCES lotes_de_cupom(id) ON DELETE CASCADE,
  service_id UUID NOT NULL,
  PRIMARY KEY (lote_id, service_id)
);
