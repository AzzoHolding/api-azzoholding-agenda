-- V149 (BNC-009): as tabelas do caixa passam a exigir que o vinculo seja do MESMO salao.
-- As FKs antigas so olhavam o id: dava para uma comanda apontar para agendamento, cliente ou
-- profissional de outro salao (ja houve incidente: outro salao abria comanda com o id de um
-- agendamento daqui e consumia o sinal; ver ServicoComanda). appointments ja usava FK composta
-- (tenant_id, client_id) desde a V1; aqui o mesmo para comandas, itens, pagamentos e sinais.
--
-- Conferido em producao antes (19/10/2026): nenhuma das tabelas tem gatilho; 0 vinculos entre
-- saloes em comandas->appointments, comandas->clients e appointment_deposits->appointments;
-- tabelas com no maximo 3 linhas. As FKs antigas (so por id) ficam: as compostas se somam a elas.
-- ON DELETE SET NULL (coluna) (Postgres 15+; producao em 18) zera so a referencia, nunca o tenant_id.
-- NOT VALID + VALIDATE: o padrao para tabela grande; aqui e barato, mas fica pronto para copiar.

-- Pais que ainda nao tinham UNIQUE (tenant_id, id)
CREATE UNIQUE INDEX IF NOT EXISTS uq_comandas_tenant_id ON comandas (tenant_id, id);
ALTER TABLE comandas ADD CONSTRAINT uq_comandas_tenant_id UNIQUE USING INDEX uq_comandas_tenant_id;
CREATE UNIQUE INDEX IF NOT EXISTS uq_appointment_deposits_tenant_id ON appointment_deposits (tenant_id, id);
ALTER TABLE appointment_deposits
    ADD CONSTRAINT uq_appointment_deposits_tenant_id UNIQUE USING INDEX uq_appointment_deposits_tenant_id;

-- comandas
ALTER TABLE comandas ADD CONSTRAINT fk_comandas_tenant_appointment
    FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id)
    ON DELETE SET NULL (appointment_id) NOT VALID;
ALTER TABLE comandas ADD CONSTRAINT fk_comandas_tenant_client
    FOREIGN KEY (tenant_id, client_id) REFERENCES clients (tenant_id, id)
    ON DELETE SET NULL (client_id) NOT VALID;
ALTER TABLE comandas ADD CONSTRAINT fk_comandas_tenant_gorjeta_professional
    FOREIGN KEY (tenant_id, gorjeta_professional_id) REFERENCES professionals (tenant_id, id)
    ON DELETE SET NULL (gorjeta_professional_id) NOT VALID;

-- comanda_itens
ALTER TABLE comanda_itens ADD CONSTRAINT fk_comanda_itens_tenant_comanda
    FOREIGN KEY (tenant_id, comanda_id) REFERENCES comandas (tenant_id, id)
    ON DELETE CASCADE NOT VALID;
ALTER TABLE comanda_itens ADD CONSTRAINT fk_comanda_itens_tenant_professional
    FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals (tenant_id, id)
    ON DELETE SET NULL (professional_id) NOT VALID;

-- comanda_pagamentos
ALTER TABLE comanda_pagamentos ADD CONSTRAINT fk_comanda_pagamentos_tenant_comanda
    FOREIGN KEY (tenant_id, comanda_id) REFERENCES comandas (tenant_id, id)
    ON DELETE CASCADE NOT VALID;
ALTER TABLE comanda_pagamentos ADD CONSTRAINT fk_comanda_pagamentos_tenant_deposit
    FOREIGN KEY (tenant_id, appointment_deposit_id) REFERENCES appointment_deposits (tenant_id, id)
    ON DELETE SET NULL (appointment_deposit_id) NOT VALID;

-- appointment_deposits (o sinal)
ALTER TABLE appointment_deposits ADD CONSTRAINT fk_appointment_deposits_tenant_appointment
    FOREIGN KEY (tenant_id, appointment_id) REFERENCES appointments (tenant_id, id)
    ON DELETE RESTRICT NOT VALID;
ALTER TABLE appointment_deposits ADD CONSTRAINT fk_appointment_deposits_tenant_used_in_comanda
    FOREIGN KEY (tenant_id, used_in_comanda_id) REFERENCES comandas (tenant_id, id)
    ON DELETE RESTRICT NOT VALID;

ALTER TABLE comandas VALIDATE CONSTRAINT fk_comandas_tenant_appointment;
ALTER TABLE comandas VALIDATE CONSTRAINT fk_comandas_tenant_client;
ALTER TABLE comandas VALIDATE CONSTRAINT fk_comandas_tenant_gorjeta_professional;
ALTER TABLE comanda_itens VALIDATE CONSTRAINT fk_comanda_itens_tenant_comanda;
ALTER TABLE comanda_itens VALIDATE CONSTRAINT fk_comanda_itens_tenant_professional;
ALTER TABLE comanda_pagamentos VALIDATE CONSTRAINT fk_comanda_pagamentos_tenant_comanda;
ALTER TABLE comanda_pagamentos VALIDATE CONSTRAINT fk_comanda_pagamentos_tenant_deposit;
ALTER TABLE appointment_deposits VALIDATE CONSTRAINT fk_appointment_deposits_tenant_appointment;
ALTER TABLE appointment_deposits VALIDATE CONSTRAINT fk_appointment_deposits_tenant_used_in_comanda;
