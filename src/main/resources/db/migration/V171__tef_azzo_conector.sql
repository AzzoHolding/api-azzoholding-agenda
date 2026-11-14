-- Pagamentos integrados — Fase 6 (14/11/2026): TEF no balcao pelo Azzo Conector (PayGo ou SiTef).
--
-- O Azzo Conector e um programa no Windows do balcao, ligado ao pinpad. Ele busca no Azzo a venda
-- que o PDV mandou (como uma maquininha pela nuvem), faz a transacao TEF pela biblioteca da PayGo
-- (PGWebLib) ou do SiTef (CliSiTef), devolve o resultado e so CONFIRMA a transacao se o Azzo
-- aceitar (cobranca cancelada no caixa enquanto a cliente passava o cartao = transacao desfeita).
--
--  - contas_de_pagamento: operadora nova "TEF" (a configuracao diz se e PayGo ou SiTef);
--  - terminais_de_pagamento (TEF_LOCAL): o pareamento do Conector (codigo de uso unico, com prazo)
--    e o token dele — so o hash fica no banco;
--  - cobrancas: quando o Conector pegou a venda e os comprovantes do TEF (via cliente e via loja).
-- As tres tabelas sao da V167 e ainda nao existem em producao (nada publicado das fases 0 a 7).

ALTER TABLE contas_de_pagamento DROP CONSTRAINT IF EXISTS ck_contas_de_pagamento_provedor;
ALTER TABLE contas_de_pagamento ADD CONSTRAINT ck_contas_de_pagamento_provedor CHECK (
  provedor IN ('ASAAS', 'MERCADO_PAGO', 'PAGARME', 'STONE', 'CIELO', 'PAGBANK', 'REDE', 'GETNET', 'TEF'));

ALTER TABLE terminais_de_pagamento
  ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS codigo_pareamento_hash VARCHAR(64),
  ADD COLUMN IF NOT EXISTS codigo_expira_em TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS pareado_em TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS ultimo_contato_em TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS nome_do_computador VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS ux_terminais_de_pagamento_token
  ON terminais_de_pagamento (token_hash) WHERE token_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_terminais_de_pagamento_codigo
  ON terminais_de_pagamento (codigo_pareamento_hash) WHERE codigo_pareamento_hash IS NOT NULL;

ALTER TABLE cobrancas
  ADD COLUMN IF NOT EXISTS conector_pegou_em TIMESTAMP WITH TIME ZONE,
  ADD COLUMN IF NOT EXISTS comprovante_cliente TEXT,
  ADD COLUMN IF NOT EXISTS comprovante_loja TEXT;

-- A fila do Conector: as vendas do terminal que ainda esperam.
CREATE INDEX IF NOT EXISTS ix_cobrancas_fila_do_terminal
  ON cobrancas (terminal_id, created_at) WHERE status IN ('CRIADA', 'AGUARDANDO');

-- O resultado do Conector entra no historico da cobranca com origem propria.
ALTER TABLE eventos_de_cobranca DROP CONSTRAINT IF EXISTS ck_eventos_de_cobranca_origem;
ALTER TABLE eventos_de_cobranca ADD CONSTRAINT ck_eventos_de_cobranca_origem CHECK (
  origem IN ('WEBHOOK', 'CONSULTA', 'MANUAL', 'CONECTOR'));
