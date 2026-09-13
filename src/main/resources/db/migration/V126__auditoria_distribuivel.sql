-- A trilha de auditoria (/auditoria) passa a ser distribuivel nos perfis de acesso (fase F2 de
-- docs/ESPEC_PERFIS_DE_ACESSO.md, so para a auditoria). A LGPD (/auditoria/lgpd) continua
-- exclusiva do dono.
--
-- O controller aceita o papel (OWNER, FINANCE) OU a permissao audit:view. Por isso a permissao NAO
-- vai para o ADMIN: ele passaria a ler a trilha dos saloes. FINANCE nao tem linha em roles e segue
-- entrando pelo papel.

INSERT INTO permissions (id, code, description)
VALUES (public.uuid_generate_v4(), 'audit:view', 'Permite consultar e exportar a trilha de auditoria')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'audit:view'
WHERE r.name = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'audit:view'
FROM item_menu im
WHERE im.route = '/auditoria'
ON CONFLICT DO NOTHING;

UPDATE item_menu SET distribuivel = TRUE, updated_at = NOW() WHERE route = '/auditoria';
