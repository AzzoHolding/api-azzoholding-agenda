CREATE TABLE appointment_customer_notes (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  appointment_id UUID NOT NULL,
  client_id UUID NOT NULL,
  recorded_by_user_id UUID,
  service_execution_notes VARCHAR(1000),
  client_feedback_notes VARCHAR(1000),
  internal_followup_notes VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_appointment_customer_notes_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT fk_appointment_customer_notes_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
  CONSTRAINT fk_appointment_customer_notes_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);

CREATE INDEX idx_appointment_customer_notes_tenant_client_created_at
  ON appointment_customer_notes (tenant_id, client_id, created_at DESC);

CREATE INDEX idx_appointment_customer_notes_tenant_appointment
  ON appointment_customer_notes (tenant_id, appointment_id);

CREATE INDEX idx_appointment_customer_notes_tenant_user_created_at
  ON appointment_customer_notes (tenant_id, recorded_by_user_id, created_at DESC);
