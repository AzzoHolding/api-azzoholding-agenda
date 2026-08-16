-- V86: Enriquece tenant_special_closure_dates com suporte a fechamento
--      por profissional, fechamento parcial (horário) e classificação de motivo.
--
-- Contexto:
--   - Tabela criada em V55 com colunas: id, tenant_id, closure_date, reason,
--     created_at, updated_at.
--   - UNIQUE (tenant_id, closure_date) definida em V55 como
--     uk_tenant_special_closure_dates_tenant_date.
--   - Esta migration adiciona: professional_id, all_day, start_time, end_time,
--     closure_type; remove a unique antiga e cria índice único parcial que
--     suporta múltiplos fechamentos por data (salão + profissionais).

-- ============================================================
-- 1 — Novas colunas
-- ============================================================

ALTER TABLE tenant_special_closure_dates
  ADD COLUMN IF NOT EXISTS professional_id UUID
    REFERENCES professionals(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS all_day BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS start_time TIME,
  ADD COLUMN IF NOT EXISTS end_time TIME,
  ADD COLUMN IF NOT EXISTS closure_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL';

COMMENT ON COLUMN tenant_special_closure_dates.reason IS
  'Motivo opcional. Nao registrar informacoes medicas. Acesso restrito ao OWNER e ao profissional afetado.';
COMMENT ON COLUMN tenant_special_closure_dates.professional_id IS
  'Profissional afetado. Nulo = fechamento do salao inteiro.';
COMMENT ON COLUMN tenant_special_closure_dates.all_day IS
  'true = dia inteiro; false = apenas start_time ate end_time.';
COMMENT ON COLUMN tenant_special_closure_dates.closure_type IS
  'HOLIDAY | VACATION | RECESS | INTERNAL_EVENT | MANUAL';

-- ============================================================
-- 2 — Constraints de integridade
-- ============================================================

ALTER TABLE tenant_special_closure_dates
  ADD CONSTRAINT chk_partial_closure_times
    CHECK (all_day = true OR (start_time IS NOT NULL AND end_time IS NOT NULL)),
  ADD CONSTRAINT chk_closure_time_order
    CHECK (all_day = true OR end_time > start_time),
  ADD CONSTRAINT chk_closure_type
    CHECK (closure_type IN ('HOLIDAY','VACATION','RECESS','INTERNAL_EVENT','MANUAL'));

-- ============================================================
-- 3 — Substituir UNIQUE antiga por índice único parcial
--
-- A constraint uk_tenant_special_closure_dates_tenant_date (V55) impede
-- múltiplos fechamentos no mesmo dia (ex: salão + profissional A + profissional B).
-- Removemos e criamos índice único que usa COALESCE para tratar NULL como
-- sentinela, permitindo exatamente um registro por (tenant, data, profissional).
-- ============================================================

ALTER TABLE tenant_special_closure_dates
  DROP CONSTRAINT IF EXISTS uk_tenant_special_closure_dates_tenant_date;

CREATE UNIQUE INDEX IF NOT EXISTS uq_closure_tenant_date_prof
  ON tenant_special_closure_dates (
    tenant_id,
    closure_date,
    COALESCE(professional_id, '00000000-0000-0000-0000-000000000000'::uuid)
  );

-- ============================================================
-- 4 — Índices de suporte
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_closure_professional_id
  ON tenant_special_closure_dates (professional_id)
  WHERE professional_id IS NOT NULL;

-- Índice (tenant_id, closure_date) já existe como
-- idx_tenant_special_closure_dates_tenant_date (V55) — não recriar.
