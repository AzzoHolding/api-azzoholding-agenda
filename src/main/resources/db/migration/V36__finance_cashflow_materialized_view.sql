-- F1.4: Materialized View para fluxo de caixa diário por tenant
-- Usada pelo endpoint GET /api/v1/finance/cash-flow

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_finance_cashflow_daily AS
SELECT
    tenant_id,
    (date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE 0 END)          AS total_income,
    SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END)          AS total_expenses,
    SUM(CASE WHEN type = 'INCOME'  THEN amount ELSE -amount END)    AS balance
FROM transactions
GROUP BY tenant_id, (date AT TIME ZONE 'America/Sao_Paulo')::date;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_finance_cashflow_daily
    ON mv_finance_cashflow_daily (tenant_id, metric_date);

CREATE INDEX IF NOT EXISTS idx_mv_finance_cashflow_daily_tenant_date
    ON mv_finance_cashflow_daily (tenant_id, metric_date DESC);
