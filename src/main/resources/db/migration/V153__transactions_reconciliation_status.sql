-- Situacao da conciliacao: alem de conciliado ou nao, um lancamento pode estar DIVERGENTE do extrato.
-- `reconciled` continua: relatorios, CSV e filtros antigos leem o booleano. Vale true so em RECONCILED.
-- Conferido em producao em 01/11/2026: `transactions` nao tem gatilho nem CHECK.
ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS reconciled_by         UUID,
  ADD COLUMN IF NOT EXISTS reconciliation_note   TEXT,
  ADD COLUMN IF NOT EXISTS statement_amount      NUMERIC(19, 2);

UPDATE transactions SET reconciliation_status = 'RECONCILED' WHERE reconciled = TRUE;

ALTER TABLE transactions
  ADD CONSTRAINT ck_transactions_reconciliation_status
  CHECK (reconciliation_status IN ('PENDING', 'RECONCILED', 'DIVERGENT'));
