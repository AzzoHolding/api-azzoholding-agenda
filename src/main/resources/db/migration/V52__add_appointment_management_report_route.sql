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
  'db1b2c7a-7bb4-4b0f-b2a5-2c8234e71016',
  '/relatorio/agendamento',
  'Relatorio de agendamentos',
  TRUE,
  1,
  'CalendarRange',
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
WHERE child.route = '/relatorio/agendamento'
  AND parent.route = '/relatorio';

UPDATE item_menu
SET display_order = 2,
    updated_at = NOW()
WHERE route = '/relatorio/no-show';

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/relatorio/agendamento', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio/agendamento', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio/agendamento', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
