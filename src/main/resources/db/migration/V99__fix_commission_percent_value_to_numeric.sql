-- Corrige tipo de percent_value de DOUBLE PRECISION para NUMERIC(7,4)
-- DOUBLE PRECISION tem imprecisao binaria inadequada para percentuais financeiros
-- NUMERIC(7,4) suporta ate 999.9999 com 4 casas decimais (suficiente para taxas de 0 a 100%)
-- A clausula USING faz ROUND para 4 casas preservando os dados existentes com seguranca

ALTER TABLE commission_rules
  ALTER COLUMN percent_value TYPE NUMERIC(7,4) USING ROUND(percent_value::NUMERIC, 4);

ALTER TABLE commission_entries
  ALTER COLUMN percent_value TYPE NUMERIC(7,4) USING ROUND(percent_value::NUMERIC, 4);

-- Campo legado de comissao no cadastro de profissional (ainda usado como taxa simples)
ALTER TABLE professionals
  ALTER COLUMN commission_rate TYPE NUMERIC(7,4) USING ROUND(commission_rate::NUMERIC, 4);
