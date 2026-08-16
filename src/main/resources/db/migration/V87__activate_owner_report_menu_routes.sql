-- Garante que o OWNER tenha acesso a todas as paginas de relatorio
-- hoje suportadas para a empresa no menu principal.
-- Relatorio de licencas permanece fora deste pacote por ser administrativo
-- e protegido no backend apenas para a role ADMIN.

INSERT INTO item_menu (
  id,
  route,
  label,
  parent_item_menu_id,
  display_order,
  icon_key,
  is_active,
  created_at,
  updated_at
)
SELECT
  seed.id,
  seed.route,
  seed.label,
  parent.id,
  seed.display_order,
  seed.icon_key,
  TRUE,
  NOW(),
  NOW()
FROM (
  VALUES
    ('75d9777b-d3e6-4c7b-9961-991e482f2431'::uuid, '/relatorio', 'Relatorios', NULL, 11, 'BarChart3'),
    ('db1b2c7a-7bb4-4b0f-b2a5-2c8234e71016'::uuid, '/relatorio/agendamento', 'Agendamentos', '/relatorio', 1, 'BarChart3'),
    ('cbc0a347-54f9-49cb-ac56-f0de731e7efd'::uuid, '/relatorio/no-show', 'No-show', '/relatorio', 2, 'Calendar'),
    ('9e237355-ff31-429d-825f-e1ac1d98a681'::uuid, '/relatorio/abandono', 'Abandono', '/relatorio', 3, 'MessageCircleMore'),
    ('f7d8ee8d-3251-4a74-bf5f-f0d94295a7c9'::uuid, '/relatorio/financeiro', 'Financeiro', '/relatorio', 4, 'DollarSign'),
    ('20a9df93-409d-4847-af47-29259ef3af6c'::uuid, '/relatorio/estoque', 'Estoque', '/relatorio', 5, 'Package'),
    ('d600f38e-4c37-4fea-9fd7-12e73cd250c2'::uuid, '/relatorio/vendas', 'Vendas', '/relatorio', 6, 'ShoppingCart'),
    ('cb4c6b06-2da7-48fb-90d8-e64b36cda0e2'::uuid, '/relatorio/clientes', 'Clientes', '/relatorio', 7, 'UserCircle'),
    ('db0a9518-691b-4217-ad4d-5d34e10f8245'::uuid, '/relatorio/gerencial', 'Gerencial', '/relatorio', 8, 'BarChart3')
) AS seed(id, route, label, parent_route, display_order, icon_key)
LEFT JOIN item_menu parent ON parent.route = seed.parent_route
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    parent_item_menu_id = EXCLUDED.parent_item_menu_id,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    is_active = TRUE,
    updated_at = NOW();

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/relatorio', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/agendamento', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/no-show', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/abandono', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/financeiro', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/estoque', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/vendas', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/clientes', TRUE, NOW(), NOW()),
  ('OWNER', '/relatorio/gerencial', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM item_menu parent
WHERE parent.route = '/relatorio'
  AND child.route IN (
    '/relatorio/agendamento',
    '/relatorio/no-show',
    '/relatorio/abandono',
    '/relatorio/financeiro',
    '/relatorio/estoque',
    '/relatorio/vendas',
    '/relatorio/clientes',
    '/relatorio/gerencial'
  )
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;
