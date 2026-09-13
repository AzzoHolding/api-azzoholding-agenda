-- Comissoes distribuiveis nos perfis de acesso (fase F2 de docs/ESPEC_PERFIS_DE_ACESSO.md). Mesmo
-- desenho da V126 (auditoria): o controller aceita o papel OWNER OU a permissao, e por isso os
-- codigos NAO vao para o ADMIN — ele passaria a ver e pagar comissao dos saloes.
--
-- commission:view le (regras, relatorio, ciclos); commission:manage escreve (regras, fechar e
-- pagar ciclo, ajuste). A tela Comissoes concede os dois (decisao D2: so "tem acesso ou nao").

INSERT INTO permissions (id, code, description)
VALUES
  (public.uuid_generate_v4(), 'commission:view', 'Permite consultar regras, lancamentos e ciclos de comissao'),
  (public.uuid_generate_v4(), 'commission:manage', 'Permite editar regras, fechar e pagar ciclos e lancar ajustes de comissao')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('commission:view', 'commission:manage')
WHERE r.name = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

WITH mapa(route, code) AS (
  VALUES
    ('/financeiro/comissoes', 'commission:view'),
    ('/financeiro/comissoes', 'commission:manage'),
    ('/financeiro/comissoes/:professionalId', 'commission:view'),
    ('/financeiro/comissoes/:professionalId', 'commission:manage')
)
INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, m.code
FROM mapa m
JOIN item_menu im ON im.route = m.route
ON CONFLICT DO NOTHING;

UPDATE item_menu SET distribuivel = TRUE, updated_at = NOW()
WHERE route IN ('/financeiro/comissoes', '/financeiro/comissoes/:professionalId');

-- O editor de regras por profissional do frontend antigo e detalhe de /profissionais (rota com
-- parametro segue a base), mas o que ele faz e comissao: sem isto, quem recebe Profissionais veria
-- a tela e tomaria 403. As regras continuam editaveis pela tela Comissoes.
UPDATE item_menu SET distribuivel = FALSE, updated_at = NOW()
WHERE route = '/profissionais/:id/comissao';
