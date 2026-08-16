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
  'e46a28f8-5898-4075-b1a2-702cd4ecc895',
  '/perfil-usuario',
  'Perfil do Usuario',
  TRUE,
  1,
  'User',
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
WHERE child.route = '/perfil-usuario'
  AND parent.route = '/configuracoes'
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/perfil-usuario', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/perfil-usuario', TRUE, NOW(), NOW()),
  ('ADMIN', '/perfil-usuario', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
