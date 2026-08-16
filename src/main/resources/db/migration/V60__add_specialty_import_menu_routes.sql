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
  'c5c32a7c-e2b3-4e69-9e03-2fbb0a67195a',
  '/especialidades/importacoes',
  'Importacoes de especialidades',
  parent.id,
  TRUE,
  51,
  'Download',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/especialidades'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/especialidades/importacoes'
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
  '9fe340d5-4beb-436c-b990-a4319fdd29f2',
  '/especialidades/importacoes/:jobId',
  'Detalhe da importacao de especialidades',
  parent.id,
  TRUE,
  52,
  'Download',
  NOW(),
  NOW()
FROM item_menu parent
WHERE parent.route = '/especialidades/importacoes'
  AND NOT EXISTS (
    SELECT 1
    FROM item_menu existing
    WHERE existing.route = '/especialidades/importacoes/:jobId'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/especialidades/importacoes',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/especialidades'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/especialidades/importacoes'
  );

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT
  parent_perm.role,
  '/especialidades/importacoes/:jobId',
  parent_perm.is_active,
  NOW(),
  NOW()
FROM menu_role_permissions parent_perm
WHERE parent_perm.route = '/especialidades/importacoes'
  AND UPPER(parent_perm.role) IN ('OWNER', 'PROFESSIONAL')
  AND NOT EXISTS (
    SELECT 1
    FROM menu_role_permissions existing
    WHERE existing.role = parent_perm.role
      AND existing.route = '/especialidades/importacoes/:jobId'
  );
