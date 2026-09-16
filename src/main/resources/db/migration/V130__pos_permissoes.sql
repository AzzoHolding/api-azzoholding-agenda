-- O PDV passa a exigir permissao, e nao so papel (achado do teste de ponta a ponta de
-- 2026-09-16: o `ComandaController` tinha apenas `hasAnyRole('OWNER','PROFESSIONAL','STAFF')`, e
-- quem tinha o papel operava o caixa aberto mesmo com um perfil de acesso SEM `/pos` — o menu
-- escondia a tela e a API aceitava a chamada).
--
-- pos:view le comandas; pos:manage mexe no dinheiro (item, desconto, gorjeta, pagamento,
-- fidelidade, fechar, cancelar, estornar).
--
-- QUEM RECEBE, e por que:
--   OWNER        — papel do dono, como nos demais codigos;
--   PROFESSIONAL — e a ferramenta do trabalho dele (a comanda do proprio atendimento). Sem este
--                  grant, todo profissional perderia o PDV no deploy;
--   STAFF        — NAO recebe pelo papel, de proposito: e o papel da recepcao, e o acesso dela vem
--                  do PERFIL. Quem esta em "Acesso completo" continua com o PDV (o teto do dono
--                  inclui `/pos`); quem tem perfil restrito SEM `/pos` passa a ser recusado pela
--                  API tambem, que e o objetivo.
-- ADMIN nao recebe: seria o administrador do sistema operando o caixa do salao.

INSERT INTO permissions (id, code, description)
VALUES
  (public.uuid_generate_v4(), 'pos:view', 'Permite consultar comandas do PDV'),
  (public.uuid_generate_v4(), 'pos:manage', 'Permite lancar itens, desconto, gorjeta, pagamento e fechar comanda no PDV')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('pos:view', 'pos:manage')
WHERE r.name IN ('OWNER', 'PROFESSIONAL')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- A rota do PDV passa a conceder os codigos novos quando um perfil de acesso a inclui. Os codigos
-- antigos do mapa (`appointment:read`, `professional:read`) continuam: a tela tambem le agenda e
-- equipe para montar a comanda.
WITH mapa(route, code) AS (
  VALUES
    ('/pos', 'pos:view'),
    ('/pos', 'pos:manage'),
    ('/pos/:id', 'pos:view'),
    ('/pos/:id', 'pos:manage')
)
INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, m.code
FROM mapa m
JOIN item_menu im ON im.route = m.route
ON CONFLICT DO NOTHING;
