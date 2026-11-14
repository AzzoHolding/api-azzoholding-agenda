-- Fidelidade (14/11/2026): pontos devolvidos voltam com o vencimento ORIGINAL ou com vencimento NOVO,
-- escolha de quem desfaz o resgate, cancela ou estorna (decisao do dono). Escolher "novo" e do dono
-- ou de quem tem a opcao no perfil de acesso; sem escolha, volta o original.
--
-- consumos_de_pontos: de qual credito saiu cada resgate, para devolver ao mesmo credito (com o
-- vencimento dele). Tabela nova.
--
-- Opcao do perfil (mesmo modelo da V157/V173): pos:points-expiry, item oculto /pos/vencimento-dos-pontos.
-- Conferido em producao em 14/11/2026 (so leitura, ao preparar a V173): permissions, item_menu,
-- menu_role_permissions e item_menu_permissao sem gatilho nem CHECK.

CREATE TABLE IF NOT EXISTS consumos_de_pontos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  resgate_id UUID NOT NULL REFERENCES movimentos_de_pontos(id) ON DELETE CASCADE,
  credito_id UUID NOT NULL REFERENCES movimentos_de_pontos(id) ON DELETE CASCADE,
  comanda_id UUID,
  pontos INTEGER NOT NULL,
  devolvido BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT ck_consumos_de_pontos_pontos CHECK (pontos > 0)
);
CREATE INDEX IF NOT EXISTS ix_consumos_de_pontos_comanda ON consumos_de_pontos (comanda_id) WHERE NOT devolvido;

INSERT INTO permissions (id, code, description)
VALUES (gen_random_uuid(), 'pos:points-expiry',
        'Permite escolher se os pontos devolvidos voltam com vencimento novo ou com o original')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES (gen_random_uuid(), '/pos/vencimento-dos-pontos', 'Escolher o vencimento dos pontos devolvidos', TRUE, 505,
        'ShoppingCart', FALSE, NOW(), NOW())
ON CONFLICT (route) DO NOTHING;

UPDATE item_menu filho
SET parent_item_menu_id = pai.id, updated_at = NOW()
FROM item_menu pai
WHERE filho.route = '/pos/vencimento-dos-pontos'
  AND pai.route = '/pos';

INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'OWNER', '/pos/vencimento-dos-pontos', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/pos/vencimento-dos-pontos', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/pos/vencimento-dos-pontos', FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, 'pos:points-expiry'
FROM item_menu im
WHERE im.route = '/pos/vencimento-dos-pontos'
ON CONFLICT DO NOTHING;
