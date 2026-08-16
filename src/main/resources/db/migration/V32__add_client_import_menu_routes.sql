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
  '9a96d5e0-6c46-4970-8d08-0e2a1770f5c0',
  '/clientes/importacoes',
  'Importacoes de clientes',
  parent.id,
  TRUE,
  72,
  'Download',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/clientes'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/clientes/importacoes'
  );

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
  'fef985e1-05d7-4d9f-85ad-27df6fda2d53',
  '/clientes/importacoes/:jobId',
  'Detalhe da importacao de clientes',
  parent.id,
  TRUE,
  73,
  'Download',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/clientes/importacoes'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/clientes/importacoes/:jobId'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/clientes/importacoes',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/clientes'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/clientes/importacoes'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/clientes/importacoes/:jobId',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/clientes/importacoes'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/clientes/importacoes/:jobId'
  );
