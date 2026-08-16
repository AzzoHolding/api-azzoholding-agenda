-- Backfill do catalogo administrativo de menus em ambientes que ja tinham
-- item_menu populado antes do seed unificado de hierarquia/permissoes.

WITH menu_hierarchy(route, parent_route) AS (
  VALUES
    ('/profissionais/:id', '/profissionais'),
    ('/profissionais/:id/comissao', '/profissionais'),
    ('/clientes/:id', '/clientes'),
    ('/chat/:conversationId', '/chat'),
    ('/estoque/visao-geral', '/estoque'),
    ('/estoque/itens', '/estoque'),
    ('/estoque/itens/novo', '/estoque/itens'),
    ('/estoque/itens/:id/editar', '/estoque/itens'),
    ('/estoque/movimentacoes', '/estoque'),
    ('/estoque/movimentacoes/nova', '/estoque/movimentacoes'),
    ('/estoque/importacoes', '/estoque'),
    ('/estoque/importacoes/:jobId', '/estoque/importacoes'),
    ('/estoque/inventarios', '/estoque'),
    ('/estoque/inventarios/novo', '/estoque/inventarios'),
    ('/estoque/inventarios/:id', '/estoque/inventarios'),
    ('/estoque/fornecedores', '/estoque'),
    ('/estoque/pedidos-compra', '/estoque'),
    ('/estoque/pedidos-compra/:id', '/estoque/pedidos-compra'),
    ('/estoque/transferencias', '/estoque'),
    ('/financeiro/comissoes', '/financeiro'),
    ('/financeiro/comissoes/:professionalId', '/financeiro/comissoes'),
    ('/financeiro/profissionais', '/financeiro'),
    ('/financeiro/licenca', '/financeiro'),
    ('/auditoria/lgpd', '/auditoria'),
    ('/configuracoes/estoque', '/configuracoes'),
    ('/configuracoes/fiscal/impostos', '/configuracoes'),
    ('/configuracoes/fiscal/certificados', '/configuracoes'),
    ('/configuracoes/fiscal/nfse', '/configuracoes'),
    ('/configuracoes/integracoes/whatsapp', '/configuracoes'),
    ('/configuracoes/admin-sistema', '/configuracoes'),
    ('/fiscal/nfse/nova', '/fiscal/nfse'),
    ('/fiscal/nfse/:id', '/fiscal/nfse'),
    ('/fiscal/nfse/:id/editar', '/fiscal/nfse'),
    ('/fiscal/nfse/:id/pdf', '/fiscal/nfse')
)
UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM menu_hierarchy seed
JOIN item_menu parent ON parent.route = seed.parent_route
WHERE child.route = seed.route
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

WITH supported_roles(role_name) AS (
  VALUES ('ADMIN'), ('OWNER'), ('PROFESSIONAL')
),
menu_hierarchy(route, parent_route) AS (
  VALUES
    ('/profissionais/:id', '/profissionais'),
    ('/profissionais/:id/comissao', '/profissionais'),
    ('/clientes/:id', '/clientes'),
    ('/chat/:conversationId', '/chat'),
    ('/estoque/visao-geral', '/estoque'),
    ('/estoque/itens', '/estoque'),
    ('/estoque/itens/novo', '/estoque/itens'),
    ('/estoque/itens/:id/editar', '/estoque/itens'),
    ('/estoque/movimentacoes', '/estoque'),
    ('/estoque/movimentacoes/nova', '/estoque/movimentacoes'),
    ('/estoque/importacoes', '/estoque'),
    ('/estoque/importacoes/:jobId', '/estoque/importacoes'),
    ('/estoque/inventarios', '/estoque'),
    ('/estoque/inventarios/novo', '/estoque/inventarios'),
    ('/estoque/inventarios/:id', '/estoque/inventarios'),
    ('/estoque/fornecedores', '/estoque'),
    ('/estoque/pedidos-compra', '/estoque'),
    ('/estoque/pedidos-compra/:id', '/estoque/pedidos-compra'),
    ('/estoque/transferencias', '/estoque'),
    ('/financeiro/comissoes', '/financeiro'),
    ('/financeiro/comissoes/:professionalId', '/financeiro/comissoes'),
    ('/financeiro/profissionais', '/financeiro'),
    ('/financeiro/licenca', '/financeiro'),
    ('/auditoria/lgpd', '/auditoria'),
    ('/configuracoes/estoque', '/configuracoes'),
    ('/configuracoes/fiscal/impostos', '/configuracoes'),
    ('/configuracoes/fiscal/certificados', '/configuracoes'),
    ('/configuracoes/fiscal/nfse', '/configuracoes'),
    ('/configuracoes/integracoes/whatsapp', '/configuracoes'),
    ('/configuracoes/admin-sistema', '/configuracoes'),
    ('/fiscal/nfse/nova', '/fiscal/nfse'),
    ('/fiscal/nfse/:id', '/fiscal/nfse'),
    ('/fiscal/nfse/:id/editar', '/fiscal/nfse'),
    ('/fiscal/nfse/:id/pdf', '/fiscal/nfse')
)
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  roles.role_name,
  child.route,
  COALESCE(parent_perm.is_active, FALSE),
  NOW(),
  NOW()
FROM menu_hierarchy seed
JOIN item_menu child ON child.route = seed.route
JOIN supported_roles roles ON TRUE
LEFT JOIN item_menu parent ON parent.route = seed.parent_route
LEFT JOIN menu_role_permissions parent_perm
  ON parent_perm.route = parent.route
 AND UPPER(parent_perm.role) = roles.role_name
LEFT JOIN menu_role_permissions existing
  ON existing.route = child.route
 AND UPPER(existing.role) = roles.role_name
WHERE existing.id IS NULL;
