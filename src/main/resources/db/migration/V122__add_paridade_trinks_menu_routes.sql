-- Registra no catalogo de menus as rotas de frontend trazidas do backlog de
-- paridade competitiva (F00/F01/F04/F10/F16/F18), liberando visibilidade para
-- OWNER. Sem esta migration o gate de MenuPermissionsContext bloqueia acesso
-- mesmo com a rota existindo no React Router.

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES
  (gen_random_uuid(), '/pos', 'Comanda / PDV', TRUE, 500, 'ShoppingCart', TRUE, NOW(), NOW()),
  (gen_random_uuid(), '/pos/:id', 'Detalhe da comanda', TRUE, 501, 'ShoppingCart', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/pacotes', 'Pacotes de servicos', TRUE, 510, 'Package', TRUE, NOW(), NOW()),
  (gen_random_uuid(), '/assinaturas-clientes', 'Clube de assinaturas', TRUE, 520, 'CreditCard', TRUE, NOW(), NOW()),
  (gen_random_uuid(), '/configuracoes/integracoes/pagamentos', 'Pagamentos (Asaas)', TRUE, 350, 'CreditCard', TRUE, NOW(), NOW()),
  (gen_random_uuid(), '/relatorio/ocupacao', 'Ocupacao (heatmap)', TRUE, 9, 'BarChart3', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/relatorio/catalogo', 'Catalogo de relatorios', TRUE, 10, 'BarChart3', FALSE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    sidebar_visible = EXCLUDED.sidebar_visible,
    updated_at = NOW();

WITH menu_hierarchy(route, parent_route) AS (
  VALUES
    ('/pos/:id', '/pos'),
    ('/configuracoes/integracoes/pagamentos', '/configuracoes'),
    ('/relatorio/ocupacao', '/relatorio'),
    ('/relatorio/catalogo', '/relatorio')
)
UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM menu_hierarchy seed
JOIN item_menu parent ON parent.route = seed.parent_route
WHERE child.route = seed.route
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/pos', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/pos', FALSE, NOW(), NOW()),
  ('ADMIN', '/pos', FALSE, NOW(), NOW()),
  ('OWNER', '/pos/:id', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/pos/:id', FALSE, NOW(), NOW()),
  ('ADMIN', '/pos/:id', FALSE, NOW(), NOW()),
  ('OWNER', '/pacotes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/pacotes', FALSE, NOW(), NOW()),
  ('ADMIN', '/pacotes', FALSE, NOW(), NOW()),
  ('OWNER', '/assinaturas-clientes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/assinaturas-clientes', FALSE, NOW(), NOW()),
  ('ADMIN', '/assinaturas-clientes', FALSE, NOW(), NOW()),
  ('OWNER', '/configuracoes/integracoes/pagamentos', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/configuracoes/integracoes/pagamentos', FALSE, NOW(), NOW()),
  ('ADMIN', '/configuracoes/integracoes/pagamentos', FALSE, NOW(), NOW()),
  ('OWNER', '/relatorio/ocupacao', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio/ocupacao', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio/ocupacao', FALSE, NOW(), NOW()),
  ('OWNER', '/relatorio/catalogo', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio/catalogo', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio/catalogo', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
