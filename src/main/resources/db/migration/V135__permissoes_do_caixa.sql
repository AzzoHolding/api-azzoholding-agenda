-- O caixa ganha permissoes proprias (achado da jornada de usuario de 2026-09-17).
--
-- Ate aqui a funcionalidade "Fechamento de Caixa" concedia `finance:view` + `finance:manage`: o
-- dono montava um perfil "Recepcao" para abrir e fechar o caixa e, sem perceber, liberava tambem
-- lancar receita e despesa, editar e apagar lancamentos, categorias e recorrencias do financeiro.
--
-- cash:view   — consultar os caixas do salao;
-- cash:manage — abrir, fechar (com a contagem) e remover o caixa.
--
-- NINGUEM PERDE ACESSO NO DEPLOY:
--   * papeis: quem tinha `finance:view` pelo papel recebe `cash:view`; quem tinha `finance:manage`
--     recebe `cash:manage` (OWNER, ADMIN; o PROFESSIONAL continua so consultando);
--   * perfis: a funcionalidade "Fechamento de Caixa" troca finance:* por cash:*; e "Resumo
--     Financeiro" (`/financeiro`), que ja dava finance:manage, passa a dar cash:* tambem — quem
--     cuida do financeiro inteiro continua operando o caixa.
-- O que muda e so o que devia mudar: o perfil que tem o caixa e NAO tem o financeiro deixa de
-- lancar no financeiro.

INSERT INTO permissions (id, code, description)
VALUES
  (public.uuid_generate_v4(), 'cash:view', 'Permite consultar os caixas do salao'),
  (public.uuid_generate_v4(), 'cash:manage', 'Permite abrir, fechar e remover o caixa')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, novo.id
FROM role_permissions rp
JOIN permissions antigo ON antigo.id = rp.permission_id
JOIN permissions novo
  ON (antigo.code = 'finance:view' AND novo.code = 'cash:view')
  OR (antigo.code = 'finance:manage' AND novo.code = 'cash:manage')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM item_menu_permissao imp
USING item_menu im
WHERE imp.item_menu_id = im.id
  AND im.route = '/financeiro/fechamento-caixa'
  AND imp.permission_code IN ('finance:view', 'finance:manage');

WITH mapa(route, code) AS (
  VALUES
    ('/financeiro/fechamento-caixa', 'cash:view'),
    ('/financeiro/fechamento-caixa', 'cash:manage'),
    ('/financeiro', 'cash:view'),
    ('/financeiro', 'cash:manage')
)
INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, m.code
FROM mapa m
JOIN item_menu im ON im.route = m.route
ON CONFLICT DO NOTHING;
