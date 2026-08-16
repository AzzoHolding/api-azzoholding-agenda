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
   public.uuid_generate_v4(),
  '/agenda/no-show',
  'Relatorio de no-show',
  TRUE,
  31,
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
WHERE child.route = '/agenda/no-show'
  AND parent.route = '/agenda'
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/agenda/no-show', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/agenda/no-show', FALSE, NOW(), NOW()),
  ('ADMIN', '/agenda/no-show', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
