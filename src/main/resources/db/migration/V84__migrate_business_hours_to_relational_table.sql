-- V84: Migra horários de funcionamento do JSON legado para a tabela relacional
-- e adiciona suporte a pausa (intervalo de almoço/descanso) na tenant_business_hours.
--
-- Contexto:
--   - tenant_operational_settings.business_hours_json (TEXT, criado em V43) armazena
--     um array JSON com o formato:
--       [{"day":"Segunda-feira","enabled":true,"open":"09:00","close":"19:00"}, ...]
--   - Os valores do campo "day" são em português, produzidos pelo método
--     defaultBusinessHoursList() em TenantOperationalSettingsService.
--   - tenant_business_hours (criada em V75) é a nova fonte de verdade; usa
--     day_of_week como VARCHAR com valores em inglês maiúsculo (MONDAY, TUESDAY...).
--
-- Parte 1: Adicionar colunas de pausa
-- Parte 2: Migrar dados do JSON para a tabela relacional (somente tenants sem linhas)
-- Parte 3: Garantir 7 dias para todos os tenants (fallback com defaults do sistema)

-- ============================================================
-- Parte 1 — Adicionar suporte a intervalo de pausa
-- ============================================================

ALTER TABLE tenant_business_hours
  ADD COLUMN IF NOT EXISTS break_start TIME,
  ADD COLUMN IF NOT EXISTS break_end   TIME;

COMMENT ON COLUMN tenant_business_hours.break_start IS 'Início da pausa (ex: almoço). Nulo se não houver pausa.';
COMMENT ON COLUMN tenant_business_hours.break_end   IS 'Fim da pausa. Deve ser preenchido junto com break_start.';

-- ============================================================
-- Parte 2 — Migrar business_hours_json → tenant_business_hours
--
-- Só migra tenants que:
--   (a) possuem business_hours_json preenchido e diferente de '[]'
--   (b) ainda não têm nenhuma linha em tenant_business_hours
--
-- O campo "day" no JSON usa nomes em português conforme defaultBusinessHoursList():
--   "Segunda-feira", "Terca-feira", "Quarta-feira", "Quinta-feira",
--   "Sexta-feira", "Sabado", "Domingo"
--
-- O mapeamento usa startswith do prefixo (mesmo critério do método toBusinessHoursKey()).
-- ============================================================

DO $$
DECLARE
  r         RECORD;
  elem      JSONB;
  day_raw   TEXT;
  mapped    TEXT;
  open_t    TIME;
  close_t   TIME;
  is_enabled BOOLEAN;
BEGIN
  FOR r IN
    SELECT t.id AS tenant_id, tos.business_hours_json
    FROM tenant_operational_settings tos
    JOIN tenants t ON t.id = tos.tenant_id
    WHERE tos.business_hours_json IS NOT NULL
      AND tos.business_hours_json <> '[]'
      AND tos.business_hours_json <> ''
      -- só migra tenants sem linhas na tabela relacional
      AND NOT EXISTS (
        SELECT 1 FROM tenant_business_hours bh WHERE bh.tenant_id = t.id
      )
  LOOP
    BEGIN
      FOR elem IN
        SELECT *
        FROM jsonb_array_elements(r.business_hours_json::jsonb)
      LOOP
        day_raw    := elem->>'day';
        is_enabled := COALESCE((elem->>'enabled')::BOOLEAN, true);

        BEGIN
          open_t  := (elem->>'open')::TIME;
          close_t := (elem->>'close')::TIME;
        EXCEPTION WHEN OTHERS THEN
          open_t  := '09:00'::TIME;
          close_t := '19:00'::TIME;
        END;

        -- Mapeamento: prefixos portugueses → enum inglês
        -- Critério idêntico ao toBusinessHoursKey() no TenantOperationalSettingsService
        mapped := CASE
          WHEN day_raw IS NULL             THEN NULL
          WHEN LOWER(day_raw) LIKE 'seg%'  THEN 'MONDAY'
          WHEN LOWER(day_raw) LIKE 'ter%'  THEN 'TUESDAY'
          WHEN LOWER(day_raw) LIKE 'qua%'  THEN 'WEDNESDAY'
          WHEN LOWER(day_raw) LIKE 'qui%'  THEN 'THURSDAY'
          WHEN LOWER(day_raw) LIKE 'sex%'  THEN 'FRIDAY'
          WHEN LOWER(day_raw) LIKE 'sab%'  THEN 'SATURDAY'
          WHEN LOWER(day_raw) LIKE 'dom%'  THEN 'SUNDAY'
          -- caso o valor já esteja em inglês (edge case futuro)
          WHEN UPPER(day_raw) IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY',
                                   'FRIDAY','SATURDAY','SUNDAY')
               THEN UPPER(day_raw)
          ELSE NULL
        END;

        IF mapped IS NULL THEN
          RAISE NOTICE 'Tenant %: valor de dia desconhecido "%" — ignorado', r.tenant_id, day_raw;
          CONTINUE;
        END IF;

        INSERT INTO tenant_business_hours
          (id, tenant_id, day_of_week, open_time, close_time, enabled, created_at, updated_at)
        VALUES
          (gen_random_uuid(), r.tenant_id, mapped, open_t, close_t, is_enabled, NOW(), NOW())
        ON CONFLICT (tenant_id, day_of_week) DO UPDATE
          SET open_time  = EXCLUDED.open_time,
              close_time = EXCLUDED.close_time,
              enabled    = EXCLUDED.enabled,
              updated_at = NOW();

      END LOOP;

    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE 'Tenant %: erro ao processar business_hours_json (%), pulando migração automática. Detalhe: %',
        r.tenant_id, r.business_hours_json, SQLERRM;
    END;
  END LOOP;
END $$;

-- ============================================================
-- Parte 3 — Garantir 7 dias para todos os tenants
--
-- Defaults alinhados com defaultBusinessHoursList() e initDefaultBusinessHours()
-- do TenantOperationalSettingsService:
--   Seg–Sex: 09:00–19:00 habilitado
--   Sábado:  09:00–17:00 habilitado
--   Domingo: 09:00–13:00 desabilitado
-- ============================================================

INSERT INTO tenant_business_hours
  (id, tenant_id, day_of_week, open_time, close_time, enabled, created_at, updated_at)
SELECT
  gen_random_uuid(),
  t.id,
  d.day_of_week,
  d.open_t::TIME,
  d.close_t::TIME,
  d.is_enabled,
  NOW(),
  NOW()
FROM tenants t
CROSS JOIN (VALUES
  ('MONDAY',    '09:00', '19:00', true),
  ('TUESDAY',   '09:00', '19:00', true),
  ('WEDNESDAY', '09:00', '19:00', true),
  ('THURSDAY',  '09:00', '19:00', true),
  ('FRIDAY',    '09:00', '19:00', true),
  ('SATURDAY',  '09:00', '17:00', true),
  ('SUNDAY',    '09:00', '13:00', false)
) AS d(day_of_week, open_t, close_t, is_enabled)
WHERE NOT EXISTS (
  SELECT 1
  FROM tenant_business_hours bh
  WHERE bh.tenant_id = t.id
    AND bh.day_of_week = d.day_of_week
);
