-- Profissional ve do cliente so nome e telefone (decisao do dono, 02/11/2026): a agenda precisa buscar
-- cliente, mas CPF, e-mail, endereco e notas nao sao para quem nao recebeu a tela Clientes.
--
-- `/clients` so pedia appointment:read, que todo profissional tem. Esta permissao vem junto com a tela
-- Clientes no perfil de acesso (item_menu_permissao, V125), sem opcao nova no editor. Conferido em
-- producao em 02/11/2026: item_menu_permissao e permissions nao tem gatilho.
INSERT INTO permissions (id, code, description)
VALUES (gen_random_uuid(), 'client:full', 'Permite ver e alterar a ficha completa do cliente')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'client:full'
FROM item_menu im
WHERE im.route IN ('/clientes', '/clientes/:id')
ON CONFLICT DO NOTHING;
