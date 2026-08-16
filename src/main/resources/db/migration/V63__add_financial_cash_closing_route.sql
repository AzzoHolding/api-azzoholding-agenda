INSERT INTO item_menu (
  id,
  route,
  label,
  parent_item_menu_id,
  is_active,
  display_order,
  icon_key,
  created_at,
  updated_at
)
SELECT
  '13cb1ff5-2bc0-4775-b338-95438e29689c',
  '/financeiro/fechamento-caixa',
  'Fechamento de Caixa',
  parent.id,
  TRUE,
  1240,
  'Wallet',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/financeiro'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/financeiro/fechamento-caixa'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/financeiro/fechamento-caixa',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/financeiro'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL', 'ADMIN')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/financeiro/fechamento-caixa'
  );
