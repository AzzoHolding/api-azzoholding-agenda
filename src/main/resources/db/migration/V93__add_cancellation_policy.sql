ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS cancellation_policy_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS cancellation_min_hours_before INT NOT NULL DEFAULT 24,
  ADD COLUMN IF NOT EXISTS cancellation_fee_percent INT NOT NULL DEFAULT 0;
