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
  'f261a4de-7ce6-4cbd-8df1-881118e76784',
  '/servicos/importacoes',
  'Importacoes de servicos',
  parent.id,
  TRUE,
  42,
  'Download',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/servicos'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/servicos/importacoes'
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
  'e8927267-b1ba-499d-af93-bf21ddd9374f',
  '/servicos/importacoes/:jobId',
  'Detalhe da importacao de servicos',
  parent.id,
  TRUE,
  43,
  'Download',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/servicos/importacoes'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/servicos/importacoes/:jobId'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/servicos/importacoes',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/servicos'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/servicos/importacoes'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/servicos/importacoes/:jobId',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/servicos/importacoes'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/servicos/importacoes/:jobId'
  );
