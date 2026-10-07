-- BNC-001 / BNC-002 (auditoria de 2026-10-06): o banco passa a garantir o que so o Java garantia.
--
-- 1) "Um atendimento, uma cobranca": no maximo UMA comanda ABERTA ou FECHADA por agendamento.
--    Antes, duas requisicoes simultaneas (ex.: "Abrir comanda" no PDV e o atendimento passando para
--    "Em andamento") liam a lista vazia e criavam as duas: duas receitas, duas baixas de estoque,
--    duas comissoes. ESTORNADA e CANCELADA nao contam (depois delas, pode abrir outra).
-- 2) "Um sinal, um pagamento": o mesmo sinal (appointment_deposit_id) nao entra em dois pagamentos
--    nao estornados. Antes, duas comandas do mesmo agendamento abatiam o mesmo sinal.
-- 3) appointment_deposits.used_in_comanda_id era UUID puro, sem FK: passa a apontar para a comanda.
--    RESTRICT de proposito: apagar a comanda devolveria o sinal como "livre" e permitiria usa-lo de
--    novo.
--
-- Aditiva e sem alterar dados. Conferido em producao (so leitura) antes de escrever: sem gatilhos
-- nessas tres tabelas, 0 agendamentos com mais de uma comanda ativa, 0 sinais em mais de um
-- pagamento, 0 linhas em appointment_deposits.

CREATE UNIQUE INDEX IF NOT EXISTS uq_comandas_agendamento_ativa
  ON comandas (appointment_id)
  WHERE appointment_id IS NOT NULL AND status IN ('ABERTA', 'FECHADA');

CREATE UNIQUE INDEX IF NOT EXISTS uq_comanda_pagamentos_sinal
  ON comanda_pagamentos (appointment_deposit_id)
  WHERE appointment_deposit_id IS NOT NULL AND status <> 'ESTORNADO';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'fk_appointment_deposits_used_in_comanda'
      AND conrelid = 'appointment_deposits'::regclass
  ) THEN
    ALTER TABLE appointment_deposits
      ADD CONSTRAINT fk_appointment_deposits_used_in_comanda
      FOREIGN KEY (used_in_comanda_id) REFERENCES comandas (id) ON DELETE RESTRICT;
  END IF;
END $$;
