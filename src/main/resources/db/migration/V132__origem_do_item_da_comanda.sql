-- Origem do item da comanda (achado do teste de ponta a ponta de 2026-09-16, item 7): o servico
-- que o AGENDAMENTO trouxe e o lancado a mao no PDV eram indistinguiveis, e lancar o mesmo servico
-- de novo cobrava o cliente duas vezes sem aviso nenhum.
--
-- As linhas que ja existem viram MANUAL por padrao. As que vieram de agendamento sao recuperadas
-- pelo vinculo da comanda com o agendamento: se a comanda tem appointment_id e o item e de um
-- servico desse agendamento (appointment_items), ele veio de la.

ALTER TABLE comanda_itens
  ADD COLUMN IF NOT EXISTS origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

UPDATE comanda_itens i
SET origem = 'AGENDAMENTO'
FROM comandas c, appointment_items ai
WHERE i.comanda_id = c.id
  AND ai.appointment_id = c.appointment_id
  AND i.tipo = 'SERVICO'
  AND i.referencia_id = ai.service_id
  AND i.origem = 'MANUAL';
