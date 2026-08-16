-- F2.1: Conciliação de lançamentos
ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS reconciled    BOOLEAN      NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ;

-- F2.3: Soft delete de lançamentos
ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS deleted_by UUID;

-- Índice para filtrar excluídos eficientemente
CREATE INDEX IF NOT EXISTS idx_transactions_deleted_at
  ON transactions (tenant_id, deleted_at)
  WHERE deleted_at IS NULL;
