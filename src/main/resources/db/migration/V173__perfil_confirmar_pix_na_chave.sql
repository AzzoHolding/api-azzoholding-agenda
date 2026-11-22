-- Opcao do perfil de acesso: confirmar o Pix na chave do salao ("Recebi" no PDV). Decisao do dono
-- (14/11/2026): quem fica no caixa e a atendente ou um profissional, e e o DONO quem escolhe, no
-- perfil, quem pode dar baixa num Pix que o Azzo nao confere sozinho. Mesmo modelo da V157: item
-- oculto na barra, filho de /pos, ligado a um codigo de permissao.
--   pos:pix-confirm  confirmar que o Pix na chave do salao chegou na conta
-- O dono confirma sempre (papel OWNER); os outros so com um perfil que tenha a opcao.
-- Conferido em producao em 14/11/2026 (so leitura): permissions, item_menu, menu_role_permissions,
-- item_menu_permissao e role_permissions sem gatilho nem CHECK; a rota ainda nao existe.
INSERT INTO permissions (id, code, description)
VALUES (gen_random_uuid(), 'pos:pix-confirm', 'Permite confirmar no PDV o Pix recebido na chave do salao')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES (gen_random_uuid(), '/pos/confirmar-pix', 'Confirmar Pix recebido na chave do salão', TRUE, 504, 'ShoppingCart', FALSE, NOW(), NOW())
ON CONFLICT (route) DO NOTHING;

UPDATE item_menu filho
SET parent_item_menu_id = pai.id, updated_at = NOW()
FROM item_menu pai
WHERE filho.route = '/pos/confirmar-pix'
  AND pai.route = '/pos';

INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'OWNER', '/pos/confirmar-pix', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/pos/confirmar-pix', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/pos/confirmar-pix', FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'pos:pix-confirm'
FROM item_menu im
WHERE im.route = '/pos/confirmar-pix'
ON CONFLICT DO NOTHING;
