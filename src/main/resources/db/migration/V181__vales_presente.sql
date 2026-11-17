-- Vales-presente (item 6 da analise de 15/11/2026; plano em PLANO-CUPONS-VALES.md) e a tela de cupons e vales.
-- Decisoes do dono (17/11/2026): receita no dia da venda (como pacote); pagar com vale nao gera receita de novo;
-- validade padrao de 12 meses (o dono muda por vale); usa quem tem o codigo; uso em partes.
-- Conferido em producao em 17/11/2026 (so leitura): comanda_itens e comanda_pagamentos sem gatilho; as CHECKs de
-- tipo e de meio sao as de V116 e V168, refeitas aqui com o valor novo; nenhum valor existente fica de fora.

CREATE TABLE IF NOT EXISTS vales_presente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  codigo VARCHAR(12) NOT NULL,
  valor_inicial NUMERIC(10, 2) NOT NULL,
  saldo NUMERIC(10, 2) NOT NULL,
  expira_em DATE NOT NULL,
  comprador_client_id UUID,
  destinatario_nome VARCHAR(120),
  mensagem VARCHAR(300),
  comanda_venda_id UUID,
  comanda_item_id UUID,
  status VARCHAR(10) NOT NULL DEFAULT 'ATIVO',
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT ck_vales_presente_valor CHECK (valor_inicial > 0),
  -- O debito so acontece com UPDATE ... WHERE saldo >= :valor: o saldo nunca fica negativo.
  CONSTRAINT ck_vales_presente_saldo CHECK (saldo >= 0),
  CONSTRAINT ck_vales_presente_status CHECK (status IN ('ATIVO', 'ESGOTADO', 'VENCIDO', 'CANCELADO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_vales_presente_tenant_codigo ON vales_presente (tenant_id, codigo);
CREATE INDEX IF NOT EXISTS idx_vales_presente_comprador ON vales_presente (tenant_id, comprador_client_id);
CREATE INDEX IF NOT EXISTS idx_vales_presente_vencimento ON vales_presente (expira_em) WHERE status = 'ATIVO';
CREATE UNIQUE INDEX IF NOT EXISTS uq_vales_presente_item ON vales_presente (comanda_item_id) WHERE comanda_item_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS movimentos_de_vale (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  vale_id UUID NOT NULL REFERENCES vales_presente(id) ON DELETE CASCADE,
  tipo VARCHAR(12) NOT NULL,
  valor NUMERIC(10, 2) NOT NULL,
  saldo_apos NUMERIC(10, 2) NOT NULL,
  comanda_id UUID,
  motivo VARCHAR(200),
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT ck_movimentos_de_vale_tipo CHECK (
    tipo IN ('VENDA', 'USO', 'DEVOLUCAO', 'ESTORNO', 'EXPIRACAO', 'AJUSTE', 'PRORROGACAO'))
);

CREATE INDEX IF NOT EXISTS idx_movimentos_de_vale_vale ON movimentos_de_vale (vale_id, created_at);

-- A comanda vende vale (item VALE) e recebe com vale (meio VALE_PRESENTE).
ALTER TABLE comanda_itens DROP CONSTRAINT IF EXISTS comanda_itens_tipo_check;
ALTER TABLE comanda_itens ADD CONSTRAINT comanda_itens_tipo_check CHECK (
  tipo IN ('SERVICO', 'PRODUTO', 'PACOTE', 'VALE'));

-- Destinatario e mensagem do vale vendido ficam no item ate o fechamento, quando o vale nasce.
ALTER TABLE comanda_itens
  ADD COLUMN IF NOT EXISTS vale_destinatario VARCHAR(120),
  ADD COLUMN IF NOT EXISTS vale_mensagem VARCHAR(300),
  ADD COLUMN IF NOT EXISTS vale_validade_meses INTEGER;

ALTER TABLE comanda_pagamentos DROP CONSTRAINT IF EXISTS comanda_pagamentos_meio_check;
ALTER TABLE comanda_pagamentos ADD CONSTRAINT comanda_pagamentos_meio_check CHECK (
  meio IN ('DINHEIRO', 'PIX_ASAAS', 'CARTAO_CREDITO_EXTERNO', 'CARTAO_DEBITO_EXTERNO', 'CREDITO_SINAL',
           'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'LINK', 'VALE_PRESENTE'));

ALTER TABLE comanda_pagamentos
  ADD COLUMN IF NOT EXISTS vale_presente_id UUID;

-- A tela de cupons e vales (zona financeiro, em /pos/cupons-e-vales: o prefixo /pos ja e da zona financeiro, sem
-- mudar o pacote do shell): item visivel na barra, distribuivel no perfil. Dar este item a um perfil e a opcao
-- "gerenciar cupons e vales-presente" (pos:coupons). O dono sempre pode.
INSERT INTO permissions (id, code, description)
VALUES (gen_random_uuid(), 'pos:coupons', 'Permite criar e editar cupons de desconto e vales-presente')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at)
VALUES (gen_random_uuid(), '/pos/cupons-e-vales', 'Cupons e vales-presente', TRUE, 515, 'Tag', TRUE, NOW(), NOW())
ON CONFLICT (route) DO NOTHING;

INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'OWNER', '/pos/cupons-e-vales', TRUE, NOW(), NOW()),
  (gen_random_uuid(), 'PROFESSIONAL', '/pos/cupons-e-vales', FALSE, NOW(), NOW()),
  (gen_random_uuid(), 'ADMIN', '/pos/cupons-e-vales', FALSE, NOW(), NOW())
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, code
FROM item_menu im
CROSS JOIN (VALUES ('pos:coupons'), ('pos:view')) AS p(code)
WHERE im.route = '/pos/cupons-e-vales'
ON CONFLICT DO NOTHING;

-- O dono tem as permissoes pelo papel (role_permissions), como pos:view/pos:manage (V130).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'pos:coupons'
WHERE r.name = 'OWNER'
ON CONFLICT DO NOTHING;
