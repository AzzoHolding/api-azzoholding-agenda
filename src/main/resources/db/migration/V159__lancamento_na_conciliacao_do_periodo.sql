-- Liga o lancamento a conciliacao de dia ou de mes que o conciliou, para a aba Conciliacoes abrir o grupo.
-- Conferido em producao em 06/11/2026: `transactions` sem gatilho, 1 CHECK (situacao), 25 linhas.
ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS reconciliation_period_id UUID NULL REFERENCES reconciliation_periods(id);

CREATE INDEX IF NOT EXISTS idx_transactions_reconciliation_period
  ON transactions (reconciliation_period_id)
  WHERE reconciliation_period_id IS NOT NULL;

-- As conciliacoes feitas antes desta coluna: o lancamento foi conciliado na mesma transacao que gravou o
-- periodo, um instante antes dele. Vale o primeiro periodo gravado depois do lancamento, em ate 1 minuto.
UPDATE transactions t
SET reconciliation_period_id = (
  SELECT p.id
  FROM reconciliation_periods p
  WHERE p.tenant_id = t.tenant_id
    AND p.created_at >= t.reconciled_at
    AND p.created_at <= t.reconciled_at + INTERVAL '1 minute'
  ORDER BY p.created_at
  LIMIT 1)
WHERE t.reconciliation_status = 'RECONCILED'
  AND t.reconciliation_note LIKE 'Conciliação de %'
  AND t.reconciliation_period_id IS NULL;
