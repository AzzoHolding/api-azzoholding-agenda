-- Duas opcoes do perfil de acesso, configuradas pelo dono (decisao de 02/11/2026): agendar para outros
-- profissionais, e editar e cancelar o que e deles. Sem elas, o profissional fica na propria agenda.
--
-- Entra como funcionalidade do menu, oculta na barra, porque e assim que o editor de perfis monta as
-- opcoes (V125): cada item e uma caixa de marcar, ligado a codigos de permissao. A rota nao tem tela.
-- Conferido em producao em 02/11/2026: item_menu, permissions e menu_role_permissions nao tem
-- gatilho nem CHECK.
INSERT INTO permissions (id, code, description)
VALUES
  (gen_random_uuid(), 'appointment:others', 'Permite ver a agenda de todos e agendar para outros profissionais'),
  (gen_random_uuid(), 'appointment:others-edit', 'Permite ver a agenda de todos e editar ou cancelar agendamentos de outros profissionais')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES
  (gen_random_uuid(), '/agenda/outros-profissionais', 'Agendar para outros profissionais', TRUE, 32, 'Calendar', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/agenda/editar-outros-profissionais', 'Editar e cancelar agendamentos de outros profissionais', TRUE, 33, 'Calendar', FALSE, NOW(), NOW())
ON CONFLICT (route) DO NOTHING;

UPDATE item_menu filho
SET parent_item_menu_id = pai.id, updated_at = NOW()
FROM item_menu pai
WHERE filho.route IN ('/agenda/outros-profissionais', '/agenda/editar-outros-profissionais')
  AND pai.route = '/agenda';

-- O dono pode distribuir (o perfil so concede o que esta no teto dele); os demais papeis nao tem por
-- padrao: quem nao recebeu um perfil com estas opcoes continua so na propria agenda.
INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'OWNER', '/agenda/outros-profissionais', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/agenda/outros-profissionais', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/agenda/outros-profissionais', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'OWNER', '/agenda/editar-outros-profissionais', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/agenda/editar-outros-profissionais', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/agenda/editar-outros-profissionais', FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, mapa.code
FROM (VALUES
  ('/agenda/outros-profissionais', 'appointment:others'),
  ('/agenda/editar-outros-profissionais', 'appointment:others-edit')
) AS mapa(route, code)
JOIN item_menu im ON im.route = mapa.route
ON CONFLICT DO NOTHING;
