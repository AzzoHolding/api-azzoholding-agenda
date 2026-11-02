-- Duas opcoes do perfil de acesso para o PDV, configuradas pelo dono (decisao de 02/11/2026). Mesmo
-- modelo da V155: item oculto na barra, ligado a um codigo de permissao.
--   pos:others        ver e operar as comandas de todos (a atendente)
--   pos:others-items  lancar servico de outro profissional e dar desconto nas proprias comandas
-- Sem elas, o profissional fica nas comandas dele, lanca so para si e nao da desconto.
-- Conferido em producao em 02/11/2026: item_menu, permissions e menu_role_permissions nao tem gatilho
-- nem CHECK; route, code e (role, route) sao unicos.
INSERT INTO permissions (id, code, description)
VALUES
  (gen_random_uuid(), 'pos:others', 'Permite ver e operar as comandas de todos os profissionais'),
  (gen_random_uuid(), 'pos:others-items', 'Permite lancar servico de outro profissional e dar desconto no PDV')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES
  (gen_random_uuid(), '/pos/comandas-de-todos', 'Ver e operar comandas de todos', TRUE, 502, 'ShoppingCart', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/pos/lancar-para-outros', 'Lançar serviço de outro profissional e dar desconto', TRUE, 503, 'ShoppingCart', FALSE, NOW(), NOW())
ON CONFLICT (route) DO NOTHING;

UPDATE item_menu filho
SET parent_item_menu_id = pai.id, updated_at = NOW()
FROM item_menu pai
WHERE filho.route IN ('/pos/comandas-de-todos', '/pos/lancar-para-outros')
  AND pai.route = '/pos';

INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'OWNER', '/pos/comandas-de-todos', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/pos/comandas-de-todos', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/pos/comandas-de-todos', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'OWNER', '/pos/lancar-para-outros', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/pos/lancar-para-outros', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/pos/lancar-para-outros', FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, mapa.code
FROM (VALUES
  ('/pos/comandas-de-todos', 'pos:others'),
  ('/pos/lancar-para-outros', 'pos:others-items')
) AS mapa(route, code)
JOIN item_menu im ON im.route = mapa.route
ON CONFLICT DO NOTHING;
