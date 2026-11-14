-- Pagamentos integrados — Fase 7 (14/11/2026): clube de assinaturas no cartao recorrente.
--
-- A assinatura passa a poder ser cobrada pela operadora conectada (Mercado Pago, nesta fase): o
-- Azzo cria a assinatura la, a cliente cadastra o cartao NA PAGINA DA OPERADORA (o cartao nunca
-- passa pelo Azzo) e a operadora cobra todo mes. Cada cobranca paga renova o periodo e os saldos.
--
-- Novo status AGUARDANDO_CARTAO: assinatura criada, a cliente ainda nao cadastrou o cartao — os
-- beneficios so valem depois da primeira cobranca paga.
-- Conferido em producao (14/11, so leitura): sem gatilhos nesta tabela, CHECK de status igual ao
-- local (V119) e nenhuma assinatura cadastrada.

ALTER TABLE client_memberships
  ADD COLUMN IF NOT EXISTS conta_pagamento_id UUID REFERENCES contas_de_pagamento(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS provedor_recorrencia VARCHAR(30),
  ADD COLUMN IF NOT EXISTS assinatura_na_operadora VARCHAR(80),
  ADD COLUMN IF NOT EXISTS link_do_cartao TEXT,
  ADD COLUMN IF NOT EXISTS cobrancas_pagas_na_operadora INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS ux_client_memberships_assinatura_operadora
  ON client_memberships (provedor_recorrencia, assinatura_na_operadora)
  WHERE assinatura_na_operadora IS NOT NULL;

-- "AGUARDANDO_CARTAO" tem 17 letras e a coluna era VARCHAR(16) (V119): aumentar o tamanho de um
-- varchar nao reescreve a tabela no Postgres.
ALTER TABLE client_memberships ALTER COLUMN status TYPE VARCHAR(20);

ALTER TABLE client_memberships DROP CONSTRAINT IF EXISTS client_memberships_status_check;
ALTER TABLE client_memberships ADD CONSTRAINT client_memberships_status_check CHECK (
  status IN ('ATIVA', 'INADIMPLENTE', 'SUSPENSA', 'CANCELADA', 'AGUARDANDO_CARTAO'));
