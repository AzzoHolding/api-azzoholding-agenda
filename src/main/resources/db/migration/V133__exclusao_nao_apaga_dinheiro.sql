-- Exclusao nao apaga dinheiro nem historico (analise de 2026-09-16, achados A2 e A6).
--
-- Estas chaves eram ON DELETE CASCADE: excluir um agendamento apagava o SINAL pago e o registro
-- do atendimento; excluir um cliente apagava pacotes COMPRADOS e assinaturas; excluir um servico
-- apagava o SALDO de sessoes que os clientes ja pagaram. O codigo passou a conferir os vinculos
-- antes de excluir (VinculosDeExclusao); aqui o BANCO passa a recusar tambem, para que nenhum
-- caminho futuro apague isso em silencio.
--
-- Ficam em CASCADE, de proposito, o que e parte do proprio registro ou configuracao sem valor:
-- appointment_items (itens do agendamento), service_professionals e servico_insumo (configuracao
-- do servico) e os logs de reativacao do cliente.

ALTER TABLE appointment_deposits DROP CONSTRAINT IF EXISTS appointment_deposits_appointment_id_fkey;
ALTER TABLE appointment_deposits
  ADD CONSTRAINT appointment_deposits_appointment_id_fkey
  FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;

ALTER TABLE appointment_customer_notes DROP CONSTRAINT IF EXISTS fk_appointment_customer_notes_appointment;
ALTER TABLE appointment_customer_notes
  ADD CONSTRAINT fk_appointment_customer_notes_appointment
  FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE RESTRICT;

ALTER TABLE appointment_customer_notes DROP CONSTRAINT IF EXISTS fk_appointment_customer_notes_client;
ALTER TABLE appointment_customer_notes
  ADD CONSTRAINT fk_appointment_customer_notes_client
  FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE RESTRICT;

ALTER TABLE client_package_purchases DROP CONSTRAINT IF EXISTS client_package_purchases_client_id_fkey;
ALTER TABLE client_package_purchases
  ADD CONSTRAINT client_package_purchases_client_id_fkey
  FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE RESTRICT;

ALTER TABLE client_memberships DROP CONSTRAINT IF EXISTS client_memberships_client_id_fkey;
ALTER TABLE client_memberships
  ADD CONSTRAINT client_memberships_client_id_fkey
  FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE RESTRICT;

ALTER TABLE client_package_balances DROP CONSTRAINT IF EXISTS client_package_balances_service_id_fkey;
ALTER TABLE client_package_balances
  ADD CONSTRAINT client_package_balances_service_id_fkey
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT;

ALTER TABLE client_membership_balances DROP CONSTRAINT IF EXISTS client_membership_balances_service_id_fkey;
ALTER TABLE client_membership_balances
  ADD CONSTRAINT client_membership_balances_service_id_fkey
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT;

ALTER TABLE service_package_items DROP CONSTRAINT IF EXISTS service_package_items_service_id_fkey;
ALTER TABLE service_package_items
  ADD CONSTRAINT service_package_items_service_id_fkey
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT;

ALTER TABLE membership_plan_benefits DROP CONSTRAINT IF EXISTS membership_plan_benefits_service_id_fkey;
ALTER TABLE membership_plan_benefits
  ADD CONSTRAINT membership_plan_benefits_service_id_fkey
  FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE RESTRICT;
