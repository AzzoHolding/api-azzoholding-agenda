-- Pagamentos integrados (14/11/2026): Pix na chave do proprio salao (Pix estatico, BR Code).
--
-- O Azzo monta o QR Code com a chave Pix, o nome e a cidade que o dono cadastrou; o dinheiro cai
-- direto na conta dele, sem operadora. Sem operadora nao ha aviso: o caixa confirma o recebimento
-- no PDV ("Recebi"), e isso fica na auditoria. A tabela e da V167 e ainda nao existe em producao.

ALTER TABLE contas_de_pagamento DROP CONSTRAINT IF EXISTS ck_contas_de_pagamento_provedor;
ALTER TABLE contas_de_pagamento ADD CONSTRAINT ck_contas_de_pagamento_provedor CHECK (
  provedor IN ('ASAAS', 'MERCADO_PAGO', 'PAGARME', 'STONE', 'CIELO', 'PAGBANK', 'REDE', 'GETNET', 'TEF', 'PIX_CHAVE'));
