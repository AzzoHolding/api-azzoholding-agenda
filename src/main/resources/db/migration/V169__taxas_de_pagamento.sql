-- Pagamentos integrados — Fase 5 (14/11/2026): taxas da operadora e previsao de recebimento.
--
-- O dono informa, por conta conectada, quanto a operadora cobra (percentual + valor fixo) e em
-- quantos dias o dinheiro cai, por meio e faixa de parcelas. Quando a cobranca e paga e a operadora
-- nao informou a taxa real, o Azzo estima por esta tabela: taxa, liquido e previsao ficam na
-- cobranca (colunas da V167), a despesa "Taxa da operadora" entra no financeiro ao fechar a comanda,
-- e a tela de recebiveis mostra o que vai cair e quando.
-- Tabela nova: sem gatilho; o tenant apagado leva as taxas junto (CASCADE), como as outras da V167.

CREATE TABLE IF NOT EXISTS taxas_de_pagamento (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  conta_id UUID NOT NULL REFERENCES contas_de_pagamento(id) ON DELETE CASCADE,
  meio VARCHAR(10) NOT NULL,
  parcelas_de INTEGER NOT NULL DEFAULT 1,
  parcelas_ate INTEGER NOT NULL DEFAULT 1,
  percentual NUMERIC(6, 3) NOT NULL DEFAULT 0,
  valor_fixo NUMERIC(10, 2) NOT NULL DEFAULT 0,
  dias_para_receber INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_taxas_de_pagamento_meio CHECK (meio IN ('PIX', 'CREDITO', 'DEBITO', 'LINK')),
  CONSTRAINT ck_taxas_de_pagamento_parcelas CHECK (parcelas_de BETWEEN 1 AND 24 AND parcelas_ate BETWEEN parcelas_de AND 24),
  CONSTRAINT ck_taxas_de_pagamento_percentual CHECK (percentual >= 0 AND percentual < 100),
  CONSTRAINT ck_taxas_de_pagamento_fixo CHECK (valor_fixo >= 0),
  CONSTRAINT ck_taxas_de_pagamento_dias CHECK (dias_para_receber BETWEEN 0 AND 400)
);

CREATE INDEX IF NOT EXISTS ix_taxas_de_pagamento_conta ON taxas_de_pagamento (conta_id, meio);

-- Recebiveis: o que foi pago e ainda vai cair, por data prevista.
CREATE INDEX IF NOT EXISTS ix_cobrancas_previsao ON cobrancas (tenant_id, previsao_recebimento)
  WHERE previsao_recebimento IS NOT NULL;
