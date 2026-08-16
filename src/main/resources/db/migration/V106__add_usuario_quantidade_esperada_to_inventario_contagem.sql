-- Adiciona campos de rastreabilidade e auditoria à tabela de contagens de inventário:
--   usuario_id             → quem registrou a contagem
--   quantidade_esperada    → saldo do item no momento da contagem (snapshot)
--   updated_at             → data da última edição
--   usuario_atualizacao_id → quem editou a contagem por último

ALTER TABLE estoque_inventario_contagem
  ADD COLUMN IF NOT EXISTS usuario_id             UUID,
  ADD COLUMN IF NOT EXISTS quantidade_esperada    NUMERIC(19, 4) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS updated_at             TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS usuario_atualizacao_id UUID;

CREATE INDEX IF NOT EXISTS idx_estoque_inventario_contagem_inventario_id
  ON estoque_inventario_contagem (inventario_id, created_at DESC);
