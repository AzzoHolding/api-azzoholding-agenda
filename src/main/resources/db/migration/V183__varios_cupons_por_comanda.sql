-- Item 6: o dono escolhe se a comanda aceita mais de um cupom. Padrao: um so, como ate aqui.
ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS pos_varios_cupons_por_comanda BOOLEAN NOT NULL DEFAULT FALSE;

-- Com a opcao ligada, a comanda tem varios usos ativos. O mesmo cupom continua so uma vez por comanda.
DROP INDEX IF EXISTS uq_usos_de_cupom_comanda;
CREATE UNIQUE INDEX IF NOT EXISTS uq_usos_de_cupom_comanda_cupom
  ON usos_de_cupom (comanda_id, cupom_id) WHERE status = 'ATIVO' AND comanda_id IS NOT NULL;
