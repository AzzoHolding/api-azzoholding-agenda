INSERT INTO item_menu (
  id,
  route,
  label,
  is_active,
  display_order,
  icon_key,
  created_at,
  updated_at
)
VALUES (
  'a0bb16d1-bd39-47cf-b4f2-c3dd4cf3f8c0',
  '/relatorios',
  'Relatorios',
  TRUE,
  11,
  'BarChart3',
  NOW(),
  NOW()
)
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    updated_at = NOW();

INSERT INTO item_menu (
  id,
  route,
  label,
  is_active,
  display_order,
  icon_key,
  created_at,
  updated_at
)
VALUES (
  '5f0e9b9f-69c7-4b4d-8d7d-0bcc7d61f776',
  '/relatorios/no-show',
  'Relatorio de no-show',
  TRUE,
  1,
  'Calendar',
  NOW(),
  NOW()
)
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = EXCLUDED.is_active,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    updated_at = NOW();

UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM item_menu parent
WHERE child.route = '/relatorios/no-show'
  AND parent.route = '/relatorios'
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

UPDATE item_menu
SET is_active = FALSE,
    updated_at = NOW()
WHERE route = '/agenda/no-show';

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/relatorios', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorios', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorios', FALSE, NOW(), NOW()),
  ('OWNER', '/relatorios/no-show', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorios/no-show', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorios/no-show', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
