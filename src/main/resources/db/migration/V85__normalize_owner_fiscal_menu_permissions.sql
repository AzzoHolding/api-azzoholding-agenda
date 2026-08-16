-- Garante o catalogo e as permissoes globais do modulo fiscal para OWNER.
-- A elegibilidade final continua sendo validada em runtime pelo documento do tenant (CNPJ).

WITH fiscal_hierarchy(route, parent_route) AS (
  VALUES
    ('/configuracoes/fiscal/impostos', '/configuracoes'),
    ('/configuracoes/fiscal/certificados', '/configuracoes'),
    ('/configuracoes/fiscal/nfse', '/configuracoes'),
    ('/fiscal/nfse/nova', '/fiscal/nfse'),
    ('/fiscal/nfse/:id', '/fiscal/nfse'),
    ('/fiscal/nfse/:id/editar', '/fiscal/nfse'),
    ('/fiscal/nfse/:id/pdf', '/fiscal/nfse')
)
UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    is_active = TRUE,
    updated_at = NOW()
FROM fiscal_hierarchy hierarchy
JOIN item_menu parent ON parent.route = hierarchy.parent_route
WHERE child.route = hierarchy.route
  AND (
    child.parent_item_menu_id IS DISTINCT FROM parent.id
    OR child.is_active IS DISTINCT FROM TRUE
  );

WITH fiscal_routes(route) AS (
  VALUES
    ('/configuracoes/fiscal/impostos'),
    ('/configuracoes/fiscal/certificados'),
    ('/configuracoes/fiscal/nfse'),
    ('/fiscal/nfse'),
    ('/fiscal/nfse/nova'),
    ('/fiscal/nfse/:id'),
    ('/fiscal/nfse/:id/editar'),
    ('/fiscal/nfse/:id/pdf'),
    ('/nota-fiscal'),
    ('/emitir-nota'),
    ('/apuracao-mensal')
)
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT 'OWNER', route, TRUE, NOW(), NOW()
FROM fiscal_routes
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

WITH fiscal_routes(route) AS (
  VALUES
    ('/configuracoes/fiscal/impostos'),
    ('/configuracoes/fiscal/certificados'),
    ('/configuracoes/fiscal/nfse'),
    ('/fiscal/nfse'),
    ('/fiscal/nfse/nova'),
    ('/fiscal/nfse/:id'),
    ('/fiscal/nfse/:id/editar'),
    ('/fiscal/nfse/:id/pdf'),
    ('/nota-fiscal'),
    ('/emitir-nota'),
    ('/apuracao-mensal')
),
restricted_roles(role) AS (
  VALUES
    ('ADMIN'),
    ('PROFESSIONAL')
)
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT restricted_roles.role, fiscal_routes.route, FALSE, NOW(), NOW()
FROM restricted_roles
CROSS JOIN fiscal_routes
ON CONFLICT (role, route) DO UPDATE
SET is_active = FALSE,
    updated_at = NOW();
