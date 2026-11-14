-- Pagamentos integrados — Fase 1 (13/11/2026): a comanda cobra pela operadora conectada.
--
-- Meios novos (a cobranca e criada na operadora; o pagamento so vira CONFIRMADO quando ela avisa):
--   PIX             Pix pela conta padrao de Pix (qualquer operadora)
--   CARTAO_CREDITO  cartao na maquininha integrada (Point, Stone Connect...)
--   CARTAO_DEBITO   idem, debito
--   LINK            link de pagamento enviado ao cliente
-- Os antigos continuam: *_EXTERNO (passou em maquininha nao integrada, lancado a mao), PIX_ASAAS
-- (fluxo antigo), DINHEIRO e CREDITO_SINAL.
--
-- Status novo RECUSADO: a operadora recusou, cancelou ou a cobranca expirou — nao entra no caixa.
-- Conferido em producao (13/11, so leitura): sem gatilhos nesta tabela; as CHECKs sao as de V116.

ALTER TABLE comanda_pagamentos
  ADD COLUMN IF NOT EXISTS cobranca_id UUID REFERENCES cobrancas(id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_comanda_pagamentos_cobranca
  ON comanda_pagamentos (cobranca_id) WHERE cobranca_id IS NOT NULL;

ALTER TABLE comanda_pagamentos DROP CONSTRAINT IF EXISTS comanda_pagamentos_meio_check;
ALTER TABLE comanda_pagamentos ADD CONSTRAINT comanda_pagamentos_meio_check CHECK (
  meio IN ('DINHEIRO', 'PIX_ASAAS', 'CARTAO_CREDITO_EXTERNO', 'CARTAO_DEBITO_EXTERNO', 'CREDITO_SINAL',
           'PIX', 'CARTAO_CREDITO', 'CARTAO_DEBITO', 'LINK'));

ALTER TABLE comanda_pagamentos DROP CONSTRAINT IF EXISTS comanda_pagamentos_status_check;
ALTER TABLE comanda_pagamentos ADD CONSTRAINT comanda_pagamentos_status_check CHECK (
  status IN ('PENDENTE', 'CONFIRMADO', 'ESTORNADO', 'RECUSADO'));
