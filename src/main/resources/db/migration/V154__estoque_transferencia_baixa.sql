-- Transferencia passa a dar baixa real no envio. A coluna separa as enviadas depois desta versao das
-- antigas, que nao tiraram nada do saldo: receber uma antiga nao pode dar entrada de estoque que
-- nunca saiu. Conferido em producao em 02/11/2026: a tabela nao tem gatilho, e o unico CHECK e o da
-- quantidade.
ALTER TABLE estoque_transferencia
  ADD COLUMN IF NOT EXISTS baixa_realizada BOOLEAN NOT NULL DEFAULT FALSE;
