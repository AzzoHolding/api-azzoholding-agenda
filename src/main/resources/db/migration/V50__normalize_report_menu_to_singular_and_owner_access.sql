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
  '75d9777b-d3e6-4c7b-9961-991e482f2431',
  '/relatorio',
  'Relatorio',
  TRUE,
  11,
  'BarChart3',
  NOW(),
  NOW()
)
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    parent_item_menu_id = NULL,
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
  'cbc0a347-54f9-49cb-ac56-f0de731e7efd',
  '/relatorio/no-show',
  'Relatorio de no-show',
  TRUE,
  1,
  'Calendar',
  NOW(),
  NOW()
)
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    updated_at = NOW();

UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM item_menu parent
WHERE child.route = '/relatorio/no-show'
  AND parent.route = '/relatorio';

UPDATE item_menu
SET is_active = FALSE,
    parent_item_menu_id = NULL,
    updated_at = NOW()
WHERE route IN ('/agenda/no-show', '/relatorios', '/relatorios/no-show');

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/relatorio', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio', FALSE, NOW(), NOW()),
  ('OWNER', '/relatorio/no-show', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio/no-show', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio/no-show', FALSE, NOW(), NOW()),
  ('OWNER', '/agenda/no-show', FALSE, NOW(), NOW()),
  ('PROFESSIONAL', '/agenda/no-show', FALSE, NOW(), NOW()),
  ('ADMIN', '/agenda/no-show', FALSE, NOW(), NOW()),
  ('OWNER', '/relatorios', FALSE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorios', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorios', FALSE, NOW(), NOW()),
  ('OWNER', '/relatorios/no-show', FALSE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorios/no-show', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorios/no-show', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
