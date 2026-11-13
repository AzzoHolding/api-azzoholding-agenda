-- Ranking dos profissionais com os criterios do dono (08/11/2026).
--
-- O dono monta quantos rankings quiser, cada um com nome, modo de pontuar e criterios:
--   PONTOS: cada criterio vale "pontos a cada X unidades" (ex.: 1 ponto a cada R$ 100; -3 por falta);
--   PESO:   cada criterio vira nota de 0 a 100 contra o melhor do periodo, com peso em % (soma 100).
-- O catalogo de criterios (o que cada chave mede) e fixo no backend; aqui fica so a escolha do dono.
-- visivel_para_profissional: o profissional ve a PROPRIA posicao em "Minha producao" — nunca a dos colegas.

CREATE TABLE IF NOT EXISTS ranking_profissionais (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome VARCHAR(80) NOT NULL,
  modo VARCHAR(10) NOT NULL,
  criterios_json TEXT NOT NULL,
  visivel_para_profissional BOOLEAN NOT NULL DEFAULT FALSE,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_ranking_profissionais_modo CHECK (modo IN ('PONTOS', 'PESO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ranking_profissionais_nome
  ON ranking_profissionais (tenant_id, lower(nome)) WHERE ativo;

CREATE INDEX IF NOT EXISTS ix_ranking_profissionais_tenant
  ON ranking_profissionais (tenant_id) WHERE ativo;

-- A tela fica em Relatorios (zona operacoes), so para o dono por padrao.
INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES (gen_random_uuid(), '/relatorio/ranking', 'Ranking dos profissionais', TRUE, 11, 'Trophy', FALSE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    display_order = EXCLUDED.display_order,
    icon_key = EXCLUDED.icon_key,
    sidebar_visible = EXCLUDED.sidebar_visible,
    updated_at = NOW();

UPDATE item_menu child
SET parent_item_menu_id = parent.id,
    updated_at = NOW()
FROM item_menu parent
WHERE child.route = '/relatorio/ranking'
  AND parent.route = '/relatorio'
  AND child.parent_item_menu_id IS DISTINCT FROM parent.id;

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/relatorio/ranking', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/relatorio/ranking', FALSE, NOW(), NOW()),
  ('ADMIN', '/relatorio/ranking', FALSE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, p.code
FROM item_menu im
JOIN permissions p ON p.code = 'finance:view'
WHERE im.route = '/relatorio/ranking'
ON CONFLICT DO NOTHING;
