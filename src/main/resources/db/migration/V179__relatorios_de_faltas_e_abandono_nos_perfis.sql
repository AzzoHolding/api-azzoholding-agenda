-- Relatorios de Faltas (no-show) e Abandono de volta na escolha dos perfis de acesso.
--
-- A V152 tirou /relatorio/no-show e /relatorio/abandono da escolha (distribuivel) porque nenhuma
-- zona tinha a tela e quem abria dava 404. As telas voltaram em 16/11/2026 (analise de 15/11,
-- defeito A3): agora o dono pode dar estes relatorios a outro perfil, como os outros relatorios.
-- Seguem fora da barra lateral (sidebar_visible = FALSE), como as outras abas de /relatorio.
--
-- /relatorio/agendamento continua FORA da escolha: a API so entrega ao dono
-- (GET /appointments/management-report com hasRole('OWNER')). /relatorio/financeiro tambem:
-- a rota so leva para /financeiro.
--
-- Permissoes da API que cada tela usa, somadas as do item (o perfil recebe as permissoes dos itens
-- escolhidos - PerfilAcessoRepository):
--   /relatorio/no-show  -> GET /appointments/no-show                 exige appointment:read
--   /relatorio/abandono -> GET /dashboard/metrics/whatsapp-reactivation exige dashboard:view
--
-- Conferido em producao em 16/11/2026 (so leitura): item_menu e item_menu_permissao sem gatilho nem
-- CHECK; os dois itens existem com distribuivel = FALSE e so finance:view; os dois codigos existem.

UPDATE item_menu
SET distribuivel = TRUE,
    updated_at = NOW()
WHERE route IN ('/relatorio/no-show', '/relatorio/abandono');

-- O nome que a tela mostra (a aba se chama "Faltas").
UPDATE item_menu
SET label = 'Faltas (no-show)',
    updated_at = NOW()
WHERE route = '/relatorio/no-show';

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'appointment:read'
FROM item_menu im
WHERE im.route = '/relatorio/no-show'
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'dashboard:view'
FROM item_menu im
WHERE im.route = '/relatorio/abandono'
ON CONFLICT DO NOTHING;
