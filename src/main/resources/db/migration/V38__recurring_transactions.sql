-- F2.2: Lançamentos Recorrentes
-- Tabela de templates de transações recorrentes (aluguel fixo, salários, etc.)

CREATE TABLE IF NOT EXISTS recurring_transactions (
  id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  tenant_id       UUID         NOT NULL REFERENCES tenants(id),
  type            VARCHAR(20)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
  category_id     UUID         REFERENCES transaction_categories(id),
  description     VARCHAR(500) NOT NULL,
  amount          BIGINT       NOT NULL CHECK (amount > 0),
  payment_method  VARCHAR(30)  NOT NULL,
  frequency       VARCHAR(20)  NOT NULL CHECK (frequency IN ('MONTHLY', 'WEEKLY')),
  day_of_month    SMALLINT     CHECK (day_of_month BETWEEN 1 AND 28), -- para MONTHLY
  day_of_week     SMALLINT     CHECK (day_of_week  BETWEEN 0 AND 6),  -- para WEEKLY (0=dom)
  active          BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_recurring_transactions_tenant
  ON recurring_transactions (tenant_id) WHERE active = TRUE;

-- Coluna source em transactions para distinguir automático vs manual
ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS source           VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN IF NOT EXISTS recurring_id     UUID REFERENCES recurring_transactions(id);

CREATE INDEX IF NOT EXISTS idx_transactions_recurring_id
  ON transactions (recurring_id) WHERE recurring_id IS NOT NULL;
