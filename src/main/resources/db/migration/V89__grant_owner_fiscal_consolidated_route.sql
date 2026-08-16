-- A rota /fiscal foi criada como ponto de entrada unificado do modulo fiscal
-- (substituindo /fiscal/nfse como raiz de navegacao). Esta migration:
-- 1. Garante que a entrada item_menu para /fiscal existe com a nomenclatura correta
-- 2. Concede permissao OWNER para /fiscal e todas as sub-rotas novas
-- 3. Revoga ADMIN e PROFESSIONAL (consistente com V85)

-- 1. Upsert do item raiz /fiscal
INSERT INTO item_menu (route, label, icon_key, display_order, parent_item_menu_id, is_active, created_at, updated_at)
VALUES ('/fiscal', 'Fiscal', 'Receipt', 150, NULL, TRUE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label        = 'Fiscal',
    icon_key     = 'Receipt',
    display_order = 150,
    parent_item_menu_id = NULL,
    is_active    = TRUE,
    updated_at   = NOW();

-- 2. Permissoes OWNER para /fiscal e todas as sub-rotas do modulo consolidado
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/fiscal',                             TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse',                        TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/nova',                   TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/:id',                    TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/:id/editar',             TRUE, NOW(), NOW()),
  ('OWNER', '/fiscal/nfse/:id/pdf',                TRUE, NOW(), NOW()),
  ('OWNER', '/emitir-nota',                        TRUE, NOW(), NOW()),
  ('OWNER', '/nota-fiscal',                        TRUE, NOW(), NOW()),
  ('OWNER', '/apuracao-mensal',                    TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/fiscal/impostos',      TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/fiscal/certificados',  TRUE, NOW(), NOW()),
  ('OWNER', '/configuracoes/fiscal/nfse',          TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active  = TRUE,
    updated_at = NOW();

-- 3. Bloqueia ADMIN e PROFESSIONAL para /fiscal (nova rota raiz)
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('ADMIN',        '/fiscal', FALSE, NOW(), NOW()),
  ('PROFESSIONAL', '/fiscal', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active  = FALSE,
    updated_at = NOW();
