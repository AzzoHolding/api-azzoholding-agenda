-- OWNER precisa enxergar o grupo financeiro operacional completo no menu.
-- Isso corrige o pacote SaaS da V42, que manteve apenas /financeiro/licenca
-- como permissao global explicita para OWNER.

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/financeiro', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/comissoes', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/profissionais', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/fechamento-caixa', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
