-- Fase 3 de normalizacao: o vinculo entre professionals e users e 1:1 opcional.
-- Um usuario nao pode estar associado a mais de um profissional no mesmo sistema.

CREATE UNIQUE INDEX IF NOT EXISTS uq_professionals_user_id_not_null
  ON professionals (user_id)
  WHERE user_id IS NOT NULL;
