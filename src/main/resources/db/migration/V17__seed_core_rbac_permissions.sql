-- Seed base de permissoes atomicas usadas pelos recursos protegidos.
-- Sem isso, OWNER recem-cadastrado recebe o papel mas nao encontra os codigos
-- necessarios na tabela permissions para operar os modulos principais.

INSERT INTO permissions (id, code, description)
VALUES
   (public.uuid_generate_v4(), 'professional:read', 'Permite visualizar servicos, especialidades e profissionais'),
   (public.uuid_generate_v4(), 'professional:write', 'Permite gerenciar servicos, especialidades e profissionais'),
   (public.uuid_generate_v4(), 'appointment:read', 'Permite visualizar clientes, agenda e chat'),
   (public.uuid_generate_v4(), 'appointment:write', 'Permite gerenciar clientes, agenda e chat'),
   (public.uuid_generate_v4(), 'dashboard:view', 'Permite visualizar metricas e dashboards'),
   (public.uuid_generate_v4(), 'finance:view', 'Permite visualizar dados financeiros'),
   (public.uuid_generate_v4(), 'finance:manage', 'Permite gerenciar lancamentos financeiros')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'professional:read',
  'professional:write',
  'appointment:read',
  'appointment:write',
  'dashboard:view',
  'finance:view',
  'finance:manage',
  'stock:view',
  'stock:manage',
  'notification:read',
  'notification:writer'
)
WHERE r.name = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'professional:read',
  'appointment:read',
  'appointment:write',
  'dashboard:view',
  'finance:view',
  'stock:view',
  'notification:read'
)
WHERE r.name = 'PROFESSIONAL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'professional:read',
  'professional:write',
  'appointment:read',
  'appointment:write',
  'dashboard:view',
  'finance:view',
  'finance:manage',
  'stock:view',
  'stock:manage',
  'notification:read',
  'notification:writer'
)
WHERE r.name = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
