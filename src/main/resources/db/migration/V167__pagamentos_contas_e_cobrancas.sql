-- Pagamentos integrados — Fase 0 (13/11/2026). Ver PLANO-PAGAMENTOS.md na raiz do projeto.
--
-- O dinheiro vai para a conta do PROPRIO salao em cada operadora (Mercado Pago, Stone/Pagar.me,
-- Cielo, PagBank, Rede, Getnet, Asaas). O Azzo so integra e concilia: nao guarda dinheiro nem
-- dado de cartao (maquininha, checkout ou tokenizacao da operadora).
--
-- tenant_payment_settings (V114, so Asaas, uma por salao) continua existindo nesta fase: os fluxos
-- de hoje (sinal, Pix da comanda, clube) ainda leem dela. A conta equivalente e criada pela
-- aplicacao na subida (a chave precisa ser recifrada em JSON, o que o SQL nao faz).

CREATE TABLE IF NOT EXISTS contas_de_pagamento (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  provedor VARCHAR(30) NOT NULL,
  apelido VARCHAR(60) NOT NULL,
  ambiente VARCHAR(10) NOT NULL DEFAULT 'SANDBOX',
  -- JSON com as credenciais da operadora, cifrado (EncryptionService). Nunca sai na API.
  credenciais_enc TEXT NOT NULL,
  -- Ultimos 4 da chave/conta, para a tela mostrar qual conta e sem expor nada.
  credenciais_mascara VARCHAR(40),
  webhook_token VARCHAR(80) NOT NULL,
  -- Para que esta conta e a padrao: PIX, CARTAO_PRESENCIAL, ONLINE (lista separada por virgula).
  padrao_para VARCHAR(80) NOT NULL DEFAULT '',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  validada_em TIMESTAMP WITH TIME ZONE,
  ultimo_erro VARCHAR(300),
  created_by UUID,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_contas_de_pagamento_ambiente CHECK (ambiente IN ('SANDBOX', 'PROD')),
  CONSTRAINT ck_contas_de_pagamento_provedor CHECK (
    provedor IN ('ASAAS', 'MERCADO_PAGO', 'PAGARME', 'STONE', 'CIELO', 'PAGBANK', 'REDE', 'GETNET'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_contas_de_pagamento_webhook ON contas_de_pagamento (webhook_token);
CREATE INDEX IF NOT EXISTS ix_contas_de_pagamento_tenant ON contas_de_pagamento (tenant_id) WHERE ativo;

-- Maquininhas (nuvem) e, na Fase 6, pinpads do TEF local.
CREATE TABLE IF NOT EXISTS terminais_de_pagamento (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  conta_id UUID NOT NULL REFERENCES contas_de_pagamento(id) ON DELETE CASCADE,
  serial VARCHAR(80) NOT NULL,
  modelo VARCHAR(60),
  apelido VARCHAR(60) NOT NULL,
  tipo VARCHAR(12) NOT NULL DEFAULT 'NUVEM',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_terminais_de_pagamento_tipo CHECK (tipo IN ('NUVEM', 'TEF_LOCAL'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_terminais_de_pagamento_serial ON terminais_de_pagamento (conta_id, serial);

-- Uma cobranca, em qualquer operadora: Pix, link, cartao online ou ordem para a maquininha.
CREATE TABLE IF NOT EXISTS cobrancas (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  conta_id UUID NOT NULL REFERENCES contas_de_pagamento(id),
  terminal_id UUID REFERENCES terminais_de_pagamento(id),
  provedor VARCHAR(30) NOT NULL,
  origem_tipo VARCHAR(20) NOT NULL,
  origem_id UUID,
  meio VARCHAR(12) NOT NULL,
  parcelas INT NOT NULL DEFAULT 1,
  valor NUMERIC(12, 2) NOT NULL,
  taxa NUMERIC(12, 2),
  valor_liquido NUMERIC(12, 2),
  previsao_recebimento DATE,
  status VARCHAR(16) NOT NULL DEFAULT 'CRIADA',
  id_na_operadora VARCHAR(120),
  idempotency_key VARCHAR(120) NOT NULL,
  pix_copia_e_cola TEXT,
  link_de_pagamento VARCHAR(500),
  expira_em TIMESTAMP WITH TIME ZONE,
  nsu VARCHAR(40),
  autorizacao VARCHAR(40),
  bandeira VARCHAR(30),
  cartao_final4 VARCHAR(4),
  valor_estornado NUMERIC(12, 2) NOT NULL DEFAULT 0,
  motivo_recusa VARCHAR(300),
  criada_por UUID,
  paga_em TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_cobrancas_valor CHECK (valor > 0),
  CONSTRAINT ck_cobrancas_parcelas CHECK (parcelas BETWEEN 1 AND 24),
  CONSTRAINT ck_cobrancas_meio CHECK (meio IN ('PIX', 'CREDITO', 'DEBITO', 'LINK')),
  CONSTRAINT ck_cobrancas_origem CHECK (origem_tipo IN ('COMANDA', 'SINAL', 'ASSINATURA', 'LINK', 'TESTE')),
  CONSTRAINT ck_cobrancas_status CHECK (
    status IN ('CRIADA', 'AGUARDANDO', 'PAGA', 'RECUSADA', 'CANCELADA', 'EXPIRADA', 'ESTORNADA', 'ESTORNO_PARCIAL')),
  CONSTRAINT ck_cobrancas_estorno CHECK (valor_estornado >= 0 AND valor_estornado <= valor)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_cobrancas_idempotencia ON cobrancas (tenant_id, idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cobrancas_operadora ON cobrancas (provedor, id_na_operadora)
  WHERE id_na_operadora IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_cobrancas_origem ON cobrancas (tenant_id, origem_tipo, origem_id);
CREATE INDEX IF NOT EXISTS ix_cobrancas_pendentes ON cobrancas (created_at)
  WHERE status IN ('CRIADA', 'AGUARDANDO');

-- Cada aviso da operadora (webhook) ou consulta, como chegou: e a prova do que a operadora disse,
-- e o que permite reprocessar. A aplicacao so insere e marca processado. Sem gatilho de bloqueio:
-- a exclusao de um salao (ON DELETE CASCADE) precisa conseguir levar estas linhas.
CREATE TABLE IF NOT EXISTS eventos_de_cobranca (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  cobranca_id UUID REFERENCES cobrancas(id),
  conta_id UUID REFERENCES contas_de_pagamento(id),
  provedor VARCHAR(30) NOT NULL,
  origem VARCHAR(12) NOT NULL,
  tipo VARCHAR(60) NOT NULL,
  status_informado VARCHAR(16),
  idempotency_key VARCHAR(200) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  payload_json TEXT,
  processado BOOLEAN NOT NULL DEFAULT FALSE,
  erro VARCHAR(500),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_eventos_de_cobranca_origem CHECK (origem IN ('WEBHOOK', 'CONSULTA', 'MANUAL'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_eventos_de_cobranca_idempotencia ON eventos_de_cobranca (idempotency_key);
CREATE INDEX IF NOT EXISTS ix_eventos_de_cobranca_cobranca ON eventos_de_cobranca (cobranca_id);
