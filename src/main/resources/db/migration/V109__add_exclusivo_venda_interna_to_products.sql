-- Plano exclusivo para venda interna (vendedores/staff comercial).
-- Planos marcados como exclusivos NAO aparecem na contratacao publica e so
-- podem ser ativados por fluxo interno autorizado (api-gerenciamento).
-- Default FALSE mantem todos os planos atuais como publicos (sem quebra).

ALTER TABLE products
  ADD COLUMN IF NOT EXISTS exclusivo_venda_interna BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN products.exclusivo_venda_interna IS
  'Plano exclusivo para venda interna: nao listado no checkout publico; ativacao apenas por fluxo interno autorizado, com auditoria.';

CREATE INDEX IF NOT EXISTS idx_products_exclusivo_venda_interna
  ON products (exclusivo_venda_interna)
  WHERE exclusivo_venda_interna = TRUE;
