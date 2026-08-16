-- LGPD art.11: flag para notas que contenham dados de saúde
-- appointment_customer_notes confirmado em V24: campos service_execution_notes, client_feedback_notes,
-- internal_followup_notes são VARCHAR(1000) — texto livre que pode conter alergias, condições cutâneas, etc.
ALTER TABLE appointment_customer_notes
  ADD COLUMN IF NOT EXISTS contains_health_data BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN appointment_customer_notes.contains_health_data
  IS 'LGPD art.11: indica se a nota pode conter dado de saude. Tratamento diferenciado exige consentimento explicito.';

COMMENT ON COLUMN appointment_customer_notes.service_execution_notes
  IS 'LGPD: campo de texto livre. Pode conter dado de saude (alergias, condicoes cutaneas, etc). Base legal: consentimento (art.11,II,a).';

CREATE INDEX IF NOT EXISTS idx_appt_notes_health_data
  ON appointment_customer_notes (appointment_id)
  WHERE contains_health_data = true;
