-- Usuario administrador do sistema (nao operacional de salao).
-- Credenciais iniciais:
-- usuario: admin@azzo.system
-- senha: Admin@12345
-- A senha pode ser alterada em /api/v1/users/me/password.

DO $$
DECLARE
  v_tenant_id UUID := '11111111-1111-1111-1111-111111111111';
  v_user_id UUID := '22222222-2222-2222-2222-222222222222';
  v_role_id UUID := '33333333-3333-3333-3333-333333333334';
  v_active_plan_status_id UUID;
BEGIN
  SELECT id INTO v_active_plan_status_id
  FROM plan_status
  WHERE code = 'ACTIVE'
  LIMIT 1;

  IF v_active_plan_status_id IS NULL THEN
    RAISE EXCEPTION 'PlanStatus ACTIVE nao encontrado';
  END IF;

  INSERT INTO tenants (id, name, slug, email, phone, plan_status_id, created_at)
  VALUES (
    v_tenant_id,
    'Azzo System',
    'azzo-system-admin',
    'admin@azzo.system',
    '(11) 90000-0000',
    v_active_plan_status_id,
    NOW()
  )
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO users (id, tenant_id, name, email, phone, role, password_hash, created_at)
  VALUES (
    v_user_id,
    v_tenant_id,
    'Administrador do Sistema',
    'admin@azzo.system',
    '(11) 90000-0000',
    'ADMIN',
    '$2a$12$5tDSZfg2LGTiFN9FLo0qiO8Njx95Qdl3VmWXNGPcrXMmcBpgStzD2',
    NOW()
  )
  ON CONFLICT (email) DO NOTHING;

  INSERT INTO roles (id, name, created_at)
  VALUES (v_role_id, 'ADMIN', NOW())
  ON CONFLICT (name) DO NOTHING;

  INSERT INTO user_roles (user_id, role_id)
  SELECT v_user_id, r.id
  FROM roles r
  WHERE r.name = 'ADMIN'
  ON CONFLICT (user_id, role_id) DO NOTHING;
END $$;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES ('ADMIN', '/configuracoes/admin-sistema', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE SET
  is_active = EXCLUDED.is_active,
  updated_at = NOW();
