-- Fase 1 de normalizacao: remove duplicidade de especialidades do profissional.
-- Fonte unica passa a ser o relacionamento N:N professional_specialties.

-- 1. Backfill do JSON legado professionals.specialties -> specialties/professional_specialties.
WITH raw_specialties AS (
  SELECT
    p.id AS professional_id,
    p.tenant_id,
    NULLIF(btrim(value), '') AS specialty_name
  FROM professionals p
  CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(p.specialties, '[]'::jsonb)) AS value
),
normalized_specialties AS (
  SELECT DISTINCT
    professional_id,
    tenant_id,
    specialty_name
  FROM raw_specialties
  WHERE specialty_name IS NOT NULL
)
INSERT INTO specialties (id, tenant_id, name, created_at)
SELECT
  public.uuid_generate_v4(),
  ns.tenant_id,
  ns.specialty_name,
  NOW()
FROM normalized_specialties ns
LEFT JOIN specialties s
  ON s.tenant_id = ns.tenant_id
 AND lower(btrim(s.name)) = lower(ns.specialty_name)
WHERE s.id IS NULL;

WITH normalized_specialties AS (
  SELECT DISTINCT
    p.id AS professional_id,
    p.tenant_id,
    NULLIF(btrim(value), '') AS specialty_name
  FROM professionals p
  CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(p.specialties, '[]'::jsonb)) AS value
),
resolved_specialties AS (
  SELECT
    ns.professional_id,
    s.id AS specialty_id
  FROM normalized_specialties ns
  JOIN specialties s
    ON s.tenant_id = ns.tenant_id
   AND lower(btrim(s.name)) = lower(ns.specialty_name)
  WHERE ns.specialty_name IS NOT NULL
)
INSERT INTO professional_specialties (professional_id, specialty_id)
SELECT professional_id, specialty_id
FROM resolved_specialties
ON CONFLICT (professional_id, specialty_id) DO NOTHING;

-- 2. Dedupe de specialties por tenant/nome normalizado antes da constraint.
WITH ranked_specialties AS (
  SELECT
    id,
    tenant_id,
    lower(btrim(name)) AS normalized_name,
    FIRST_VALUE(id) OVER (
      PARTITION BY tenant_id, lower(btrim(name))
      ORDER BY id
    ) AS canonical_id
  FROM specialties
),
duplicate_links AS (
  SELECT id AS duplicate_id, canonical_id
  FROM ranked_specialties
  WHERE id <> canonical_id
)
UPDATE professional_specialties ps
SET specialty_id = dl.canonical_id
FROM duplicate_links dl
WHERE ps.specialty_id = dl.duplicate_id
  AND NOT EXISTS (
    SELECT 1
    FROM professional_specialties existing
    WHERE existing.professional_id = ps.professional_id
      AND existing.specialty_id = dl.canonical_id
  );

WITH ranked_specialties AS (
  SELECT
    id,
    tenant_id,
    lower(btrim(name)) AS normalized_name,
    FIRST_VALUE(id) OVER (
      PARTITION BY tenant_id, lower(btrim(name))
      ORDER BY id
    ) AS canonical_id
  FROM specialties
)
DELETE FROM professional_specialties ps
USING ranked_specialties rs
WHERE ps.specialty_id = rs.id
  AND rs.id <> rs.canonical_id;

WITH ranked_specialties AS (
  SELECT
    id,
    tenant_id,
    lower(btrim(name)) AS normalized_name,
    FIRST_VALUE(id) OVER (
      PARTITION BY tenant_id, lower(btrim(name))
      ORDER BY id
    ) AS canonical_id
  FROM specialties
)
DELETE FROM specialties s
USING ranked_specialties rs
WHERE s.id = rs.id
  AND rs.id <> rs.canonical_id;

-- 3. Constraint de unicidade e remocao da coluna duplicada.
CREATE UNIQUE INDEX IF NOT EXISTS uq_specialties_tenant_name_normalized
  ON specialties (tenant_id, lower(btrim(name)));

ALTER TABLE professionals
  DROP COLUMN IF EXISTS specialties;
