-- Cupons de desconto (item 6 da analise de 15/11/2026; plano em PLANO-CUPONS-VALES.md).
-- Decisoes do dono (17/11/2026): cupom no PDV e no agendamento online; % ou R$; inicio e fim; limite total;
-- limite por cliente; valor minimo.
-- Conferido em producao em 17/11/2026 (so leitura): comandas e appointments sem gatilho; comandas so com o
-- CHECK de status; appointments sem CHECK. Aqui so se CRIAM tabelas e se ADICIONAM colunas opcionais.

-- Gerador de cupons (pedido do dono, 17/11/2026): um lote com N codigos de uso unico, para acompanhar quais foram
-- usados. Cada codigo e um cupom comum (cupons.lote_id), com limite_total = 1.
CREATE TABLE IF NOT EXISTS lotes_de_cupom (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome VARCHAR(80) NOT NULL,
  prefixo VARCHAR(10) NOT NULL,
  quantidade INTEGER NOT NULL,
  tipo VARCHAR(10) NOT NULL,
  valor NUMERIC(10, 2) NOT NULL,
  inicio DATE,
  fim DATE,
  valor_minimo NUMERIC(10, 2),
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT ck_lotes_de_cupom_tipo CHECK (tipo IN ('PERCENTUAL', 'VALOR')),
  CONSTRAINT ck_lotes_de_cupom_valor CHECK (valor > 0 AND (tipo <> 'PERCENTUAL' OR valor <= 100)),
  CONSTRAINT ck_lotes_de_cupom_quantidade CHECK (quantidade >= 1),
  CONSTRAINT ck_lotes_de_cupom_periodo CHECK (inicio IS NULL OR fim IS NULL OR fim >= inicio)
);

CREATE INDEX IF NOT EXISTS idx_lotes_de_cupom_tenant ON lotes_de_cupom (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS cupons (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  codigo VARCHAR(20) NOT NULL,
  descricao VARCHAR(120),
  tipo VARCHAR(10) NOT NULL,
  valor NUMERIC(10, 2) NOT NULL,
  inicio DATE,
  fim DATE,
  limite_total INTEGER,
  limite_por_cliente INTEGER,
  valor_minimo NUMERIC(10, 2),
  usos INTEGER NOT NULL DEFAULT 0,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  lote_id UUID REFERENCES lotes_de_cupom(id) ON DELETE CASCADE,
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT ck_cupons_tipo CHECK (tipo IN ('PERCENTUAL', 'VALOR')),
  CONSTRAINT ck_cupons_valor CHECK (valor > 0 AND (tipo <> 'PERCENTUAL' OR valor <= 100)),
  CONSTRAINT ck_cupons_periodo CHECK (inicio IS NULL OR fim IS NULL OR fim >= inicio),
  CONSTRAINT ck_cupons_limite_total CHECK (limite_total IS NULL OR limite_total >= 1),
  CONSTRAINT ck_cupons_limite_por_cliente CHECK (limite_por_cliente IS NULL OR limite_por_cliente >= 1),
  CONSTRAINT ck_cupons_valor_minimo CHECK (valor_minimo IS NULL OR valor_minimo > 0),
  -- O limite total nunca estoura: o uso so e reservado com UPDATE ... WHERE usos < limite_total.
  CONSTRAINT ck_cupons_usos CHECK (usos >= 0 AND (limite_total IS NULL OR usos <= limite_total))
);

-- Codigo unico por salao, sem diferenca de maiusculas.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cupons_tenant_codigo ON cupons (tenant_id, upper(codigo));
CREATE INDEX IF NOT EXISTS idx_cupons_lote ON cupons (lote_id) WHERE lote_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS usos_de_cupom (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  cupom_id UUID NOT NULL REFERENCES cupons(id) ON DELETE CASCADE,
  comanda_id UUID,
  appointment_id UUID,
  client_id UUID,
  valor_desconto NUMERIC(10, 2) NOT NULL DEFAULT 0,
  status VARCHAR(10) NOT NULL DEFAULT 'ATIVO',
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  desfeito_em TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ck_usos_de_cupom_status CHECK (status IN ('ATIVO', 'DESFEITO')),
  CONSTRAINT ck_usos_de_cupom_origem CHECK (comanda_id IS NOT NULL OR appointment_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_usos_de_cupom_cupom ON usos_de_cupom (cupom_id, status);
CREATE INDEX IF NOT EXISTS idx_usos_de_cupom_cliente ON usos_de_cupom (cupom_id, client_id) WHERE status = 'ATIVO';
-- Um uso ativo por comanda e por agendamento: aplicar duas vezes o mesmo pedido nao gasta dois usos.
CREATE UNIQUE INDEX IF NOT EXISTS uq_usos_de_cupom_comanda ON usos_de_cupom (comanda_id) WHERE status = 'ATIVO' AND comanda_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_usos_de_cupom_agendamento ON usos_de_cupom (appointment_id) WHERE status = 'ATIVO' AND appointment_id IS NOT NULL;

-- O cupom na comanda: mesmo modelo do resgate de pontos (V147). O valor faz parte de `desconto`.
ALTER TABLE comandas
  ADD COLUMN IF NOT EXISTS cupom_id UUID,
  ADD COLUMN IF NOT EXISTS valor_cupom NUMERIC(10, 2) NOT NULL DEFAULT 0;

-- O cupom usado no agendamento online. O desconto em si fica em appointment_items.discount_amount.
ALTER TABLE appointments
  ADD COLUMN IF NOT EXISTS cupom_id UUID;
