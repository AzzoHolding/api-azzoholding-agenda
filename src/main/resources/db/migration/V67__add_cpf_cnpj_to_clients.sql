ALTER TABLE clients
  ADD COLUMN IF NOT EXISTS cpf_cnpj VARCHAR(18),
  ADD COLUMN IF NOT EXISTS client_type VARCHAR(10) NOT NULL DEFAULT 'PF';

ALTER TABLE clients
  ADD CONSTRAINT ck_clients_client_type CHECK (client_type IN ('PF', 'PJ'));
