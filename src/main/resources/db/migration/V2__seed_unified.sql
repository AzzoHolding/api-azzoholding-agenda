-- UNIFIED SEED MIGRATION (DESTRUCTIVE REBASE)
-- Generated on 2026-03-11
-- Source files in db/migration_legacy/


-- >>> BEGIN V22__seed_owner_all_routes.sql

-- Garantir OWNER com acesso a todas as rotas de menu conhecidas
-- Fonte das rotas: menu_role_permissions existente + item_menu + rotas NFS-e novas
WITH all_routes AS (
  SELECT route FROM menu_role_permissions
  UNION
  SELECT route FROM item_menu
  UNION
  SELECT '/fiscal/nfse'
  UNION
  SELECT '/fiscal/nfse/nova'
  UNION
  SELECT '/fiscal/nfse/:id'
  UNION
  SELECT '/fiscal/nfse/:id/editar'
  UNION
  SELECT '/fiscal/nfse/:id/pdf'
  UNION
  SELECT '/configuracoes/fiscal/nfse'
)
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT 'OWNER', route, TRUE, NOW(), NOW()
FROM all_routes
WHERE route IS NOT NULL
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at;

-- <<< END V22__seed_owner_all_routes.sql

-- >>> BEGIN V33__seed_system_admin_user.sql

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

-- <<< END V33__seed_system_admin_user.sql

-- >>> BEGIN V40__suggestions_seed.sql

INSERT INTO item_menu (id, route, label, is_active, created_at, updated_at)
VALUES (public.uuid_generate_v4(), '/sugestoes', 'Sugestoes', TRUE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    updated_at = NOW();

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/sugestoes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/sugestoes', TRUE, NOW(), NOW()),
  ('ADMIN', '/sugestoes', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- <<< END V40__suggestions_seed.sql

-- >>> BEGIN V41__menu_catalog_routes_seed_unified.sql

WITH menu_seed(route, label, parent_route, display_order, icon_key, is_active) AS (
  VALUES
    ('/dashboard', 'Dashboard', NULL, 10, 'LayoutDashboard', TRUE),
    ('/notificacoes', 'Notificacoes', NULL, 20, 'Bell', TRUE),
    ('/agenda', 'Agenda', NULL, 30, 'Calendar', TRUE),
    ('/servicos', 'Servicos', NULL, 40, 'Scissors', TRUE),
    ('/especialidades', 'Especialidades', NULL, 50, 'Tag', TRUE),
    ('/profissionais', 'Profissionais', NULL, 60, 'Users', TRUE),
    ('/profissionais/:id', 'Detalhe do profissional', '/profissionais', 61, 'Users', TRUE),
    ('/profissionais/:id/comissao', 'Comissao do profissional', '/profissionais', 62, 'Receipt', TRUE),
    ('/clientes', 'Clientes', NULL, 70, 'UserCircle', TRUE),
    ('/clientes/:id', 'Detalhe do cliente', '/clientes', 71, 'UserCircle', TRUE),
    ('/sugestoes', 'Sugestoes', NULL, 80, 'Lightbulb', TRUE),
    ('/chat', 'Chat', NULL, 90, 'MessageCircleMore', TRUE),
    ('/chat/:conversationId', 'Detalhe da conversa', '/chat', 91, 'MessageCircleMore', TRUE),
    ('/estoque', 'Estoque', NULL, 100, 'Boxes', TRUE),
    ('/estoque/visao-geral', 'Visao geral do estoque', '/estoque', 101, 'Boxes', TRUE),
    ('/estoque/itens', 'Itens de estoque', '/estoque', 102, 'Boxes', TRUE),
    ('/estoque/itens/novo', 'Novo item de estoque', '/estoque/itens', 103, 'Boxes', TRUE),
    ('/estoque/itens/:id/editar', 'Editar item de estoque', '/estoque/itens', 104, 'Boxes', TRUE),
    ('/estoque/movimentacoes', 'Movimentacoes de estoque', '/estoque', 105, 'Boxes', TRUE),
    ('/estoque/movimentacoes/nova', 'Nova movimentacao de estoque', '/estoque/movimentacoes', 106, 'Boxes', TRUE),
    ('/estoque/importacoes', 'Importacoes de estoque', '/estoque', 107, 'Boxes', TRUE),
    ('/estoque/importacoes/:jobId', 'Detalhe da importacao', '/estoque/importacoes', 108, 'Boxes', TRUE),
    ('/estoque/inventarios', 'Inventarios de estoque', '/estoque', 109, 'Boxes', TRUE),
    ('/estoque/inventarios/novo', 'Novo inventario', '/estoque/inventarios', 110, 'Boxes', TRUE),
    ('/estoque/inventarios/:id', 'Detalhe do inventario', '/estoque/inventarios', 111, 'Boxes', TRUE),
    ('/estoque/fornecedores', 'Fornecedores', '/estoque', 112, 'Boxes', TRUE),
    ('/estoque/pedidos-compra', 'Pedidos de compra', '/estoque', 113, 'Boxes', TRUE),
    ('/estoque/pedidos-compra/:id', 'Detalhe do pedido de compra', '/estoque/pedidos-compra', 114, 'Boxes', TRUE),
    ('/estoque/transferencias', 'Transferencias de estoque', '/estoque', 115, 'Boxes', TRUE),
    ('/configuracoes/estoque', 'Configuracoes de estoque', '/configuracoes', 116, 'Boxes', TRUE),
    ('/financeiro', 'Resumo Financeiro', NULL, 120, 'DollarSign', TRUE),
    ('/financeiro/comissoes', 'Comissoes', '/financeiro', 121, 'Receipt', TRUE),
    ('/financeiro/comissoes/:professionalId', 'Detalhe de comissoes', '/financeiro/comissoes', 122, 'Receipt', TRUE),
    ('/financeiro/profissionais', 'Financeiro Profissionais', '/financeiro', 123, 'BarChart3', TRUE),
    ('/financeiro/licenca', 'Plano', '/financeiro', 124, 'CreditCard', TRUE),
    ('/auditoria', 'Auditoria', NULL, 130, 'ShieldCheck', TRUE),
    ('/auditoria/lgpd', 'LGPD Titulares', '/auditoria', 131, 'FileSearch', TRUE),
    ('/configuracoes', 'Configuracoes', NULL, 140, 'Settings', TRUE),
    ('/configuracoes/fiscal/impostos', 'Impostos', '/configuracoes', 141, 'Calculator', TRUE),
    ('/configuracoes/fiscal/certificados', 'Certificados fiscais', '/configuracoes', 142, 'ShieldCheck', TRUE),
    ('/configuracoes/fiscal/nfse', 'Configuracao NFS-e', '/configuracoes', 143, 'FileText', TRUE),
    ('/configuracoes/integracoes/whatsapp', 'Integracao WhatsApp', '/configuracoes', 144, 'MessageCircleMore', TRUE),
    ('/configuracoes/admin-sistema', 'Admin Sistema', '/configuracoes', 145, 'ShieldCheck', TRUE),
    ('/perfil-salao', 'Perfil do Salao', NULL, 150, 'Building2', TRUE),
    ('/fiscal/nfse', 'NFS-e', NULL, 160, 'FileText', TRUE),
    ('/fiscal/nfse/nova', 'Nova NFS-e', '/fiscal/nfse', 161, 'FileText', TRUE),
    ('/fiscal/nfse/:id', 'Detalhe da NFS-e', '/fiscal/nfse', 162, 'FileText', TRUE),
    ('/fiscal/nfse/:id/editar', 'Editar NFS-e', '/fiscal/nfse', 163, 'FileText', TRUE),
    ('/fiscal/nfse/:id/pdf', 'PDF da NFS-e', '/fiscal/nfse', 164, 'FileText', TRUE),
    ('/nota-fiscal', 'Pre-visualizacao de NF', NULL, 170, 'Eye', TRUE),
    ('/emitir-nota', 'Emitir Nota Fiscal', NULL, 171, 'FileText', TRUE),
    ('/apuracao-mensal', 'Apuracao Mensal', NULL, 172, 'Calculator', TRUE)
),
upserted AS (
  INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, created_at, updated_at)
  SELECT public.uuid_generate_v4(), route, label, is_active, display_order, icon_key, NOW(), NOW()
  FROM menu_seed
  ON CONFLICT (route) DO UPDATE
  SET label = EXCLUDED.label,
      is_active = EXCLUDED.is_active,
      display_order = EXCLUDED.display_order,
      icon_key = EXCLUDED.icon_key,
      updated_at = NOW()
  RETURNING route
)
UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM menu_seed seed
LEFT JOIN item_menu parent ON parent.route = seed.parent_route
WHERE child.route = seed.route
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

-- <<< END V41__menu_catalog_routes_seed_unified.sql

-- >>> BEGIN V42__menu_catalog_permissions_seed_unified.sql

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/dashboard', TRUE, NOW(), NOW()),
  ('OWNER', '/notificacoes', TRUE, NOW(), NOW()),
  ('OWNER', '/agenda', TRUE, NOW(), NOW()),
  ('OWNER', '/servicos', TRUE, NOW(), NOW()),
  ('OWNER', '/especialidades', TRUE, NOW(), NOW()),
  ('OWNER', '/profissionais', TRUE, NOW(), NOW()),
  ('OWNER', '/clientes', TRUE, NOW(), NOW()),
  ('OWNER', '/sugestoes', TRUE, NOW(), NOW()),
  ('OWNER', '/chat', TRUE, NOW(), NOW()),
  ('OWNER', '/estoque', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/comissoes', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/comissoes/:professionalId', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/profissionais', TRUE, NOW(), NOW()),
  ('OWNER', '/financeiro/licenca', TRUE, NOW(), NOW()),
  ('OWNER', '/auditoria', TRUE, NOW(), NOW()),
  ('OWNER', '/auditoria/lgpd', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/estoque', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/fiscal/impostos', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/fiscal/certificados', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/fiscal/nfse', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/integracoes/whatsapp', TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/admin-sistema', FALSE, NOW(), NOW()),
  ('OWNER', '/perfil-salao', TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse', TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/nova', TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/:id', TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/:id/editar', TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/:id/pdf', TRUE, NOW(), NOW()),
  ('OWNER', '/nota-fiscal', TRUE, NOW(), NOW()),
  ('OWNER', '/emitir-nota', TRUE, NOW(), NOW()),
  ('OWNER', '/apuracao-mensal', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/dashboard', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/notificacoes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/agenda', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/clientes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/sugestoes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/chat', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/estoque', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/financeiro/profissionais', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/configuracoes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/perfil-salao', TRUE, NOW(), NOW()),
  ('ADMIN', '/configuracoes/admin-sistema', TRUE, NOW(), NOW()),
  ('ADMIN', '/sugestoes', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- <<< END V42__menu_catalog_permissions_seed_unified.sql


