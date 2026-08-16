CREATE TABLE IF NOT EXISTS whatsapp_booking_reactivation_cycles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  client_id UUID,
  conversation_id UUID,
  user_identifier VARCHAR(60) NOT NULL,
  customer_name VARCHAR(160),
  origin_session_key VARCHAR(120),
  abandoned_at TIMESTAMP NOT NULL,
  last_stage VARCHAR(40) NOT NULL,
  last_service_id UUID,
  last_service_name VARCHAR(160),
  last_professional_id UUID,
  last_professional_name VARCHAR(160),
  last_requested_date DATE,
  last_requested_time VARCHAR(10),
  assistant_last_prompt VARCHAR(500),
  customer_last_message VARCHAR(500),
  conversation_context_json TEXT,
  status VARCHAR(30) NOT NULL,
  next_attempt_number INT NOT NULL DEFAULT 1,
  next_attempt_at TIMESTAMP,
  reactivated_at TIMESTAMP,
  converted_at TIMESTAMP,
  appointment_id_created_after_abandonment UUID,
  cancel_reason VARCHAR(60),
  responded_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_whatsapp_booking_reactivation_cycles_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT fk_whatsapp_booking_reactivation_cycles_client
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL,
  CONSTRAINT fk_whatsapp_booking_reactivation_cycles_conversation
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE SET NULL,
  CONSTRAINT fk_whatsapp_booking_reactivation_cycles_created_appointment
    FOREIGN KEY (appointment_id_created_after_abandonment) REFERENCES appointments(id) ON DELETE SET NULL,
  CONSTRAINT ck_whatsapp_booking_reactivation_cycles_stage
    CHECK (last_stage IN ('SERVICE_SELECTION', 'PROFESSIONAL_SELECTION', 'TIME_SELECTION', 'FINAL_REVIEW')),
  CONSTRAINT ck_whatsapp_booking_reactivation_cycles_status
    CHECK (status IN ('ACTIVE', 'REACTIVATED', 'CONVERTED', 'CANCELLED', 'EXHAUSTED'))
);

CREATE INDEX IF NOT EXISTS idx_whatsapp_booking_reactivation_cycles_tenant_status_attempt
  ON whatsapp_booking_reactivation_cycles (tenant_id, status, next_attempt_at);

CREATE INDEX IF NOT EXISTS idx_whatsapp_booking_reactivation_cycles_tenant_user
  ON whatsapp_booking_reactivation_cycles (tenant_id, user_identifier);

CREATE INDEX IF NOT EXISTS idx_whatsapp_booking_reactivation_cycles_tenant_client
  ON whatsapp_booking_reactivation_cycles (tenant_id, client_id);

CREATE INDEX IF NOT EXISTS idx_whatsapp_booking_reactivation_cycles_abandoned_at
  ON whatsapp_booking_reactivation_cycles (tenant_id, abandoned_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_booking_reactivation_cycles_active_user
  ON whatsapp_booking_reactivation_cycles (tenant_id, user_identifier)
  WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS whatsapp_booking_reactivation_attempts (
  id UUID PRIMARY KEY,
  cycle_id UUID NOT NULL,
  tenant_id UUID NOT NULL,
  attempt_number INT NOT NULL,
  scheduled_for TIMESTAMP NOT NULL,
  sent_at TIMESTAMP,
  status VARCHAR(20) NOT NULL,
  provider_message_id VARCHAR(120),
  error_message VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_whatsapp_booking_reactivation_attempts_cycle
    FOREIGN KEY (cycle_id) REFERENCES whatsapp_booking_reactivation_cycles(id) ON DELETE CASCADE,
  CONSTRAINT fk_whatsapp_booking_reactivation_attempts_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT ck_whatsapp_booking_reactivation_attempts_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_whatsapp_booking_reactivation_attempts_cycle_number
  ON whatsapp_booking_reactivation_attempts (cycle_id, attempt_number);

CREATE INDEX IF NOT EXISTS idx_whatsapp_booking_reactivation_attempts_tenant_status
  ON whatsapp_booking_reactivation_attempts (tenant_id, status, scheduled_for DESC);
