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
  '9e237355-ff31-429d-825f-e1ac1d98a681',
  '/relatorio/abandono',
  'Relatorio de abandono',
  TRUE,
  3,
  'MessageCircle',
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
WHERE child.route = '/relatorio/abandono'
  AND parent.route = '/relatorio'
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/relatorio/abandono', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio/abandono', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio/abandono', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
