-- Estorno de comanda fechada (F01): reverte pagamento/estoque/comissao/fidelidade/pacote de
-- uma venda ja concluida. Precisa de: (1) status novo ESTORNADA, (2) rastro de quem/quando/porque,
-- (3) quantidade de pontos de fidelidade creditados na venda original (para reverter o valor exato,
-- sem recalcular com regras que podem ter mudado desde entao), e (4) vinculo das Transacao com a
-- comanda que as originou (ate aqui as vendas nunca referenciavam a comanda de origem).

ALTER TABLE comandas DROP CONSTRAINT IF EXISTS comandas_status_check;
ALTER TABLE comandas ADD CONSTRAINT comandas_status_check
    CHECK (status IN ('ABERTA', 'FECHADA', 'CANCELADA', 'ESTORNADA'));

ALTER TABLE comandas
    ADD COLUMN IF NOT EXISTS estornado_por UUID,
    ADD COLUMN IF NOT EXISTS estornado_em TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS estorno_motivo TEXT,
    ADD COLUMN IF NOT EXISTS pontos_fidelidade_creditados INTEGER NOT NULL DEFAULT 0;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS comanda_id UUID REFERENCES comandas(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_comanda
    ON transactions (comanda_id)
    WHERE comanda_id IS NOT NULL;
