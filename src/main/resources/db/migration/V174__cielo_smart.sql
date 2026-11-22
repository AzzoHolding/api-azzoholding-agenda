-- Pagamentos integrados (14/11/2026): maquininha Cielo Smart pela nuvem (Integracao Remota / Order
-- Manager). Conta propria, com credenciais diferentes das da Cielo E-commerce (Pix). A tabela e da
-- V167 e ainda nao existe em producao.

ALTER TABLE contas_de_pagamento DROP CONSTRAINT IF EXISTS ck_contas_de_pagamento_provedor;
ALTER TABLE contas_de_pagamento ADD CONSTRAINT ck_contas_de_pagamento_provedor CHECK (
  provedor IN ('ASAAS', 'MERCADO_PAGO', 'PAGARME', 'STONE', 'CIELO', 'CIELO_SMART', 'PAGBANK', 'REDE', 'GETNET', 'TEF',
               'PIX_CHAVE'));
