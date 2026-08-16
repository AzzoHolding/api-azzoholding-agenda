CREATE TABLE IF NOT EXISTS professional_working_hours (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  professional_id UUID NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  day_of_week INTEGER NOT NULL,
  start_time TIME,
  end_time TIME,
  is_working BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_professional_working_hours_professional_id
  ON professional_working_hours (professional_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_professional_working_hours_slot
  ON professional_working_hours (
    professional_id,
    day_of_week,
    COALESCE(start_time, TIME '00:00'),
    COALESCE(end_time, TIME '00:00')
  );

INSERT INTO professional_working_hours (
  id,
  tenant_id,
  professional_id,
  day_of_week,
  start_time,
  end_time,
  is_working,
  created_at
)
SELECT
   public.uuid_generate_v4(),
  p.tenant_id,
  p.id,
  COALESCE(NULLIF(item ->> 'dayOfWeek', '')::integer, 0),
  NULLIF(item ->> 'startTime', '')::time,
  NULLIF(item ->> 'endTime', '')::time,
  COALESCE(NULLIF(item ->> 'isWorking', '')::boolean, FALSE),
  now()
FROM professionals p
CROSS JOIN LATERAL jsonb_array_elements(COALESCE(p.working_hours, '[]'::jsonb)) AS item
ON CONFLICT DO NOTHING;

ALTER TABLE professionals
  DROP COLUMN IF EXISTS working_hours;
