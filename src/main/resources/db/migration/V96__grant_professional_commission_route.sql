-- Libera acesso do PROFESSIONAL à tela de desempenho/comissões pessoais.
-- A rota /financeiro/profissionais já existe em item_menu (V11).
-- A API FinanceiroResource já aceita PROFESSIONAL no @RolesAllowed.
-- Só faltava a entrada em menu_role_permissions.

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES ('PROFESSIONAL', '/financeiro/profissionais', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
  SET is_active  = TRUE,
      updated_at = NOW();
