-- Adiciona professional_id em notifications para filtrar notificacoes
-- de agendamentos por profissional selecionado.
-- OWNER ve todas; PROFESSIONAL ve apenas as suas.
ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS professional_id UUID REFERENCES professionals(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_professional_id
  ON notifications (tenant_id, professional_id)
  WHERE professional_id IS NOT NULL;
