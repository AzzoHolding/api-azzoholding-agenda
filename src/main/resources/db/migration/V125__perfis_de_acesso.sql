-- Perfis de acesso da equipe (docs/ESPEC_PERFIS_DE_ACESSO.md).
--
-- O dono cria perfis (conjuntos de funcionalidades do menu) e atribui um ou mais a cada pessoa da
-- equipe. O acesso efetivo e a UNIAO dos perfis, sempre limitada ao que o proprio dono recebe
-- (menu_role_permissions do OWNER + sobreposicoes do salao + fiscal). Nada aqui muda o acesso de
-- quem ja existe: sem perfil, vale o papel fixo de sempre.
--
-- roles/role_permissions NAO sao reaproveitados: desde a V17 eles sao globais (uma linha
-- PROFESSIONAL para todos os saloes), e mexer neles mudaria todos os saloes de uma vez.

-- Funcionalidades que o dono nunca distribui, e as que ainda nao tem permissao no backend.
ALTER TABLE item_menu ADD COLUMN IF NOT EXISTS exclusivo_do_dono BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item_menu ADD COLUMN IF NOT EXISTS distribuivel BOOLEAN NOT NULL DEFAULT TRUE;

-- Funcionalidade -> codigos de permissao do backend. Conceder a funcionalidade concede TODOS eles
-- (decisao D2: so "tem acesso" ou "nao tem"), inclusive os de leitura das telas de que ela depende
-- (a agenda precisa listar servicos e profissionais).
CREATE TABLE IF NOT EXISTS item_menu_permissao (
  item_menu_id UUID NOT NULL REFERENCES item_menu(id) ON DELETE CASCADE,
  permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code) ON DELETE CASCADE,
  PRIMARY KEY (item_menu_id, permission_code)
);

CREATE TABLE IF NOT EXISTS perfil_acesso (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome VARCHAR(80) NOT NULL,
  descricao VARCHAR(255),
  -- "Acesso completo": acompanha o teto do dono, menos as exclusivas (decisao D5).
  acesso_total BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_perfil_acesso_tenant_nome ON perfil_acesso (tenant_id, lower(nome));
CREATE UNIQUE INDEX IF NOT EXISTS uq_perfil_acesso_total_por_tenant
  ON perfil_acesso (tenant_id) WHERE acesso_total;

CREATE TABLE IF NOT EXISTS perfil_acesso_item (
  perfil_id UUID NOT NULL REFERENCES perfil_acesso(id) ON DELETE CASCADE,
  item_menu_id UUID NOT NULL REFERENCES item_menu(id) ON DELETE CASCADE,
  PRIMARY KEY (perfil_id, item_menu_id)
);

-- Decisao D1: uma pessoa pode ter mais de um perfil.
CREATE TABLE IF NOT EXISTS usuario_perfil_acesso (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  perfil_id UUID NOT NULL REFERENCES perfil_acesso(id) ON DELETE RESTRICT,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  atribuido_por UUID REFERENCES users(id) ON DELETE SET NULL,
  atribuido_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, perfil_id)
);
CREATE INDEX IF NOT EXISTS idx_usuario_perfil_acesso_perfil ON usuario_perfil_acesso (perfil_id);
CREATE INDEX IF NOT EXISTS idx_usuario_perfil_acesso_tenant ON usuario_perfil_acesso (tenant_id);

-- Quem saiu da equipe. A linha do usuario FICA (ha tabelas que apontam para ele com RESTRICT): o
-- acesso e bloqueado trocando a senha e revogando os tokens.
CREATE TABLE IF NOT EXISTS equipe_desligamento (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  desligado_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  desligado_por UUID REFERENCES users(id) ON DELETE SET NULL
);

-- A tela de gestao: so o dono, e nunca distribuivel.
INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, exclusivo_do_dono, created_at, updated_at)
VALUES (gen_random_uuid(), '/configuracoes/acessos', 'Perfis de acesso', TRUE, 355, 'ShieldCheck', TRUE, TRUE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    sidebar_visible = EXCLUDED.sidebar_visible,
    exclusivo_do_dono = TRUE,
    updated_at = NOW();

UPDATE item_menu child
SET parent_item_menu_id = parent.id, updated_at = NOW()
FROM item_menu parent
WHERE child.route = '/configuracoes/acessos'
  AND parent.route = '/configuracoes'
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/configuracoes/acessos', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/configuracoes/acessos', FALSE, NOW(), NOW()),
  ('ADMIN', '/configuracoes/acessos', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active, updated_at = NOW();

-- R4 (decisao D3): exclusivas do dono.
UPDATE item_menu SET exclusivo_do_dono = TRUE, updated_at = NOW()
WHERE route IN (
  '/configuracoes/acessos',
  '/configuracoes',
  '/configuracoes/admin-sistema',
  '/configuracoes/integracoes/whatsapp',
  '/configuracoes/integracoes/telegram',
  '/configuracoes/integracoes/pagamentos',
  '/configuracoes/fiscal/certificados',
  '/configuracoes/fiscal/impostos',
  '/configuracoes/fiscal/nfse',
  '/financeiro/licenca',
  '/auditoria/lgpd',
  '/perfil-salao'
);

-- Fase 2: telas cujo backend ainda e so do dono (hasRole('OWNER')). Aparecem como "em breve".
UPDATE item_menu SET distribuivel = FALSE, updated_at = NOW()
WHERE route IN (
  '/financeiro/comissoes',
  '/financeiro/comissoes/:professionalId',
  '/fiscal',
  '/nota-fiscal',
  '/emitir-nota',
  '/apuracao-mensal',
  '/auditoria'
)
OR route LIKE '/fiscal/%';

WITH mapa(route, code) AS (
  VALUES
    ('/dashboard', 'dashboard:view'),
    ('/agenda', 'appointment:read'), ('/agenda', 'appointment:write'), ('/agenda', 'professional:read'),
    ('/agenda/no-show', 'appointment:read'), ('/agenda/no-show', 'appointment:write'),
    ('/clientes', 'appointment:read'), ('/clientes', 'appointment:write'),
    ('/clientes/:id', 'appointment:read'), ('/clientes/:id', 'appointment:write'),
    ('/chat', 'appointment:read'), ('/chat', 'appointment:write'),
    ('/chat/:conversationId', 'appointment:read'), ('/chat/:conversationId', 'appointment:write'),
    ('/servicos', 'professional:read'), ('/servicos', 'professional:write'),
    ('/especialidades', 'professional:read'), ('/especialidades', 'professional:write'),
    ('/profissionais', 'professional:read'), ('/profissionais', 'professional:write'),
    ('/profissionais/:id', 'professional:read'), ('/profissionais/:id', 'professional:write'),
    ('/pos', 'appointment:read'), ('/pos', 'professional:read'),
    ('/pos/:id', 'appointment:read'), ('/pos/:id', 'professional:read'),
    ('/pacotes', 'appointment:read'), ('/pacotes', 'professional:read'),
    ('/assinaturas-clientes', 'appointment:read'), ('/assinaturas-clientes', 'professional:read'),
    ('/financeiro', 'finance:view'), ('/financeiro', 'finance:manage'),
    ('/financeiro/fechamento-caixa', 'finance:view'), ('/financeiro/fechamento-caixa', 'finance:manage'),
    ('/financeiro/profissionais', 'finance:view'), ('/financeiro/profissionais', 'finance:manage'),
    ('/estoque', 'stock:view'), ('/estoque', 'stock:manage'),
    ('/configuracoes/estoque', 'stock:view'), ('/configuracoes/estoque', 'stock:manage'),
    ('/relatorio', 'finance:view'),
    ('/relatorio/abandono', 'finance:view'),
    ('/relatorio/agendamento', 'finance:view'),
    ('/relatorio/catalogo', 'finance:view'),
    ('/relatorio/clientes', 'finance:view'),
    ('/relatorio/estoque', 'finance:view'), ('/relatorio/estoque', 'stock:view'),
    ('/relatorio/financeiro', 'finance:view'),
    ('/relatorio/gerencial', 'finance:view'),
    ('/relatorio/no-show', 'finance:view'),
    ('/relatorio/ocupacao', 'finance:view'),
    ('/relatorio/vendas', 'finance:view'),
    ('/relatorios', 'finance:view'),
    ('/relatorios/no-show', 'finance:view'),
    ('/notificacoes', 'notification:read'), ('/notificacoes', 'notification:writer')
)
INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, p.code
FROM mapa m
JOIN item_menu im ON im.route = m.route
JOIN permissions p ON p.code = m.code
ON CONFLICT DO NOTHING;
