-- Vincula movimentacao de estoque ao item de comanda que a originou (consumo automatico de
-- insumos de SERVICO em comanda avulsa, sem agendamento vinculado). Espelha o padrao de
-- appointment_id (V70): usado para idempotencia, impede duplicar consumo se fechar() rodar
-- mais de uma vez sobre o mesmo item.
ALTER TABLE movimentacoes_estoque
  ADD COLUMN IF NOT EXISTS comanda_item_id UUID REFERENCES comanda_itens(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_movimentacoes_estoque_comanda_item
  ON movimentacoes_estoque (comanda_item_id)
  WHERE comanda_item_id IS NOT NULL;
