-- Fase 2 de normalizacao: remove agregados derivados de clients.
-- total_visits, total_spent e last_visit passam a ser calculados a partir de appointments concluidos.

ALTER TABLE clients
  DROP COLUMN IF EXISTS total_visits,
  DROP COLUMN IF EXISTS total_spent,
  DROP COLUMN IF EXISTS last_visit;
