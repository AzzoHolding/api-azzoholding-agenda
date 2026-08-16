-- Histórico de eventos de licença por tenant (ativações, cancelamentos, expirações, extensões de trial).
CREATE TABLE IF NOT EXISTS license_events (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  event_type   VARCHAR(60) NOT NULL,
  actor_user_id UUID,
  product_id   UUID REFERENCES products(id) ON DELETE SET NULL,
  valid_until  TIMESTAMPTZ,
  metadata     TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_license_events_tenant
  ON license_events (tenant_id, created_at DESC);

COMMENT ON COLUMN license_events.event_type IS
  'TRIAL_ACTIVATED, PLAN_ACTIVATED, PLAN_CANCELLED, PLAN_EXPIRED, TRIAL_EXTENDED, LICENSE_RELEASED';
