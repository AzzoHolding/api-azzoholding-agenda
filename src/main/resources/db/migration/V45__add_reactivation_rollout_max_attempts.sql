ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS reactivation_max_attempts_enabled INTEGER NOT NULL DEFAULT 3;

UPDATE tenant_operational_settings
SET reactivation_max_attempts_enabled = 3
WHERE reactivation_max_attempts_enabled IS NULL
   OR reactivation_max_attempts_enabled < 1
   OR reactivation_max_attempts_enabled > 3;
