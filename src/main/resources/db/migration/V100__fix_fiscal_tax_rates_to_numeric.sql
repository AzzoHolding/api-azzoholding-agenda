-- Corrige tipos de aliquotas fiscais de DOUBLE PRECISION para NUMERIC(7,4)
-- DOUBLE PRECISION tem imprecisao binaria inadequada para aliquotas tributarias
-- NUMERIC(7,4) e o tipo correto para percentuais fiscais (ex: 5.0000, 12.5000, 2.0000)
-- A clausula USING faz ROUND para 4 casas preservando os dados existentes com seguranca

ALTER TABLE fiscal_tax_configs
  ALTER COLUMN icms_rate TYPE NUMERIC(7,4) USING ROUND(icms_rate::NUMERIC, 4),
  ALTER COLUMN pis_rate TYPE NUMERIC(7,4) USING ROUND(pis_rate::NUMERIC, 4),
  ALTER COLUMN cofins_rate TYPE NUMERIC(7,4) USING ROUND(cofins_rate::NUMERIC, 4);
