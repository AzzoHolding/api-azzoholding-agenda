DELETE FROM item_menu
WHERE route IN (
  '/especialidades/importacoes/:jobId',
  '/especialidades/importacoes'
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
