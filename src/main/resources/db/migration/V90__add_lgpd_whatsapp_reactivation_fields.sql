-- V90: LGPD — opt-in/opt-out, histórico de consentimento, log de envios e config por tenant

-- 1. Opt-in/opt-out e data de nascimento na tabela clients
ALTER TABLE clients
  ADD COLUMN IF NOT EXISTS birth_date DATE,
  ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS whatsapp_opt_in_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS whatsapp_opt_out BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS whatsapp_opt_out_at TIMESTAMPTZ;

-- 2. Tabela de histórico de consentimento (append-only, NUNCA deletar)
CREATE TABLE IF NOT EXISTS reactivation_consent_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
  action VARCHAR(10) NOT NULL CHECK (action IN ('OPT_IN', 'OPT_OUT')),
  source VARCHAR(30) NOT NULL CHECK (source IN ('WHATSAPP_REPLY', 'OWNER', 'SYSTEM', 'PORTAL')),
  registered_by UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_consent_history_client ON reactivation_consent_history(client_id);
CREATE INDEX IF NOT EXISTS idx_consent_history_tenant ON reactivation_consent_history(tenant_id);

-- 3. Tabela de log de envios (sem PII — apenas IDs)
CREATE TABLE IF NOT EXISTS reactivation_send_log (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
  cycle_id UUID NOT NULL REFERENCES whatsapp_booking_reactivation_cycles(id) ON DELETE CASCADE,
  attempt_number INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SENT' CHECK (status IN ('SENT', 'DELIVERED', 'READ', 'FAILED', 'SKIPPED')),
  provider_message_id VARCHAR(255),
  error_code VARCHAR(50),
  sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  delivered_at TIMESTAMPTZ,
  read_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '1 year')
);

CREATE INDEX IF NOT EXISTS idx_reactivation_send_log_cycle ON reactivation_send_log(cycle_id);
CREATE INDEX IF NOT EXISTS idx_reactivation_send_log_client ON reactivation_send_log(client_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_reactivation_send_log_expires ON reactivation_send_log(expires_at);

-- 4. Configuração de reativação por tenant
CREATE TABLE IF NOT EXISTS tenant_reactivation_config (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  abandonment_delay_minutes INT NOT NULL DEFAULT 30 CHECK (abandonment_delay_minutes >= 15),
  max_attempts INT NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 3),
  attempt_1_delay_days INT NOT NULL DEFAULT 2 CHECK (attempt_1_delay_days >= 1),
  attempt_2_delay_days INT NOT NULL DEFAULT 4 CHECK (attempt_2_delay_days >= 2),
  attempt_3_delay_days INT NOT NULL DEFAULT 7 CHECK (attempt_3_delay_days >= 3),
  send_window_start TIME NOT NULL DEFAULT '08:00:00',
  send_window_end TIME NOT NULL DEFAULT '20:00:00',
  max_messages_per_month_per_client INT NOT NULL DEFAULT 4 CHECK (max_messages_per_month_per_client <= 4),
  min_interval_days INT NOT NULL DEFAULT 7 CHECK (min_interval_days >= 7),
  template_attempt_1 TEXT,
  template_attempt_2 TEXT,
  template_attempt_3 TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed: criar config padrão para tenants existentes
INSERT INTO tenant_reactivation_config (tenant_id)
SELECT id FROM tenants
ON CONFLICT (tenant_id) DO NOTHING;
