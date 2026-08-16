ALTER TABLE appointments
  DROP CONSTRAINT IF EXISTS fk_appointments_service_tenant;

DROP INDEX IF EXISTS idx_appointments_service_id;

ALTER TABLE appointments
  DROP COLUMN IF EXISTS service_id,
  DROP COLUMN IF EXISTS total_price;
