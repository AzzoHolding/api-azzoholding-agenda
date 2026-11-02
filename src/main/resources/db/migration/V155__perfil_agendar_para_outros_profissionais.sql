-- Opcao do perfil de acesso: agendar para outros profissionais (decisao do dono, 02/11/2026). Uma
-- atendente marca para todos; o profissional, so para si.
--
-- Entra como funcionalidade do menu, oculta na barra, porque e assim que o editor de perfis monta as
-- opcoes (V125): cada item e uma caixa de marcar, ligado a codigos de permissao. A rota nao tem tela.
-- Conferido em producao em 02/11/2026: item_menu, permissions e menu_role_permissions nao tem
-- gatilho nem CHECK.
INSERT INTO permissions (id, code, description)
VALUES (gen_random_uuid(), 'appointment:others', 'Permite agendar e atender na agenda de outros profissionais')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES (
  gen_random_uuid(), '/agenda/outros-profissionais', 'Agendar para outros profissionais',
  TRUE, 32, 'Calendar', FALSE, NOW(), NOW()
)
ON CONFLICT (route) DO NOTHING;

UPDATE item_menu filho
SET parent_item_menu_id = pai.id, updated_at = NOW()
FROM item_menu pai
WHERE filho.route = '/agenda/outros-profissionais' AND pai.route = '/agenda';

-- O dono pode distribuir (o perfil so concede o que esta no teto dele); os demais papeis nao tem por
-- padrao: quem nao recebeu um perfil com esta opcao continua so na propria agenda.
INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'OWNER', '/agenda/outros-profissionais', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/agenda/outros-profissionais', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/agenda/outros-profissionais', FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'appointment:others'
FROM item_menu im
WHERE im.route = '/agenda/outros-profissionais'
ON CONFLICT DO NOTHING;
