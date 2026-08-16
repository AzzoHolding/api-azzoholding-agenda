-- Vincula movimentacao de estoque ao appointment que a originou (consumo automatico de insumos).
-- Usado para idempotencia: impede duplicacao se o scheduler rodar mais de uma vez.
ALTER TABLE movimentacoes_estoque
  ADD COLUMN IF NOT EXISTS appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_movimentacoes_estoque_appointment
  ON movimentacoes_estoque (appointment_id)
  WHERE appointment_id IS NOT NULL;
