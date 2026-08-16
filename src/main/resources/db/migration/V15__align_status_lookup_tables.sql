-- Alinha as tabelas de lookup de status com os valores persistidos pelos
-- converters/enums do dominio. Como o projeto ainda nao foi lancado,
-- privilegia-se consistencia do modelo sobre compatibilidade com seeds antigos.

-- appointment_status
INSERT INTO appointment_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('CONFIRMED', 'Confirmado'),
  ('IN_PROGRESS', 'Em andamento'),
  ('COMPLETED', 'Concluido'),
  ('CANCELLED', 'Cancelado'),
  ('NO_SHOW', 'Nao compareceu')
ON CONFLICT (code) DO NOTHING;

UPDATE appointment_status
SET description = CASE code
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'IN_PROGRESS' THEN 'Em andamento'
  WHEN 'COMPLETED' THEN 'Concluido'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'NO_SHOW' THEN 'Nao compareceu'
  ELSE description
END
WHERE code IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW');

UPDATE appointments
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'IN_PROGRESS' THEN 'Em andamento'
  WHEN 'COMPLETED' THEN 'Concluido'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'NO_SHOW' THEN 'Nao compareceu'
  ELSE status
END
WHERE status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW');

DELETE FROM appointment_status
WHERE code NOT IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW');

-- checkout_status
INSERT INTO checkout_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('PROCESSING', 'Processando'),
  ('CONFIRMED', 'Confirmado'),
  ('EXPIRED', 'Expirado'),
  ('CANCELLED', 'Cancelado'),
  ('FAILED', 'Falhou')
ON CONFLICT (code) DO NOTHING;

UPDATE checkout_status
SET description = CASE code
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Processando'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE description
END
WHERE code IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

UPDATE checkout_intents
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Processando'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

UPDATE orders
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Processando'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

DELETE FROM checkout_status
WHERE code NOT IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

-- subscription_status
INSERT INTO subscription_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('ACTIVE', 'Ativo'),
  ('OVERDUE', 'Atrasado'),
  ('CANCELLED', 'Cancelado'),
  ('INACTIVE', 'Inativo')
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_status
SET description = CASE code
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'ACTIVE' THEN 'Ativo'
  WHEN 'OVERDUE' THEN 'Atrasado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'INACTIVE' THEN 'Inativo'
  ELSE description
END
WHERE code IN ('PENDING', 'ACTIVE', 'OVERDUE', 'CANCELLED', 'INACTIVE');

UPDATE subscriptions
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'ACTIVE' THEN 'Ativo'
  WHEN 'OVERDUE' THEN 'Atrasado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'INACTIVE' THEN 'Inativo'
  WHEN 'TRIAL' THEN 'Ativo'
  WHEN 'PAST_DUE' THEN 'Atrasado'
  WHEN 'EXPIRED' THEN 'Inativo'
  ELSE status
END
WHERE status IN ('PENDING', 'ACTIVE', 'OVERDUE', 'CANCELLED', 'INACTIVE', 'TRIAL', 'PAST_DUE', 'EXPIRED');

DELETE FROM subscription_status
WHERE code NOT IN ('PENDING', 'ACTIVE', 'OVERDUE', 'CANCELLED', 'INACTIVE');

-- payment_status
INSERT INTO payment_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('RECEIVED', 'Recebido'),
  ('CONFIRMED', 'Confirmado'),
  ('OVERDUE', 'Atrasado'),
  ('CANCELLED', 'Cancelado'),
  ('REFUNDED', 'Estornado'),
  ('FAILED', 'Falhou'),
  ('OTHER', 'Outro')
ON CONFLICT (code) DO NOTHING;

UPDATE payment_status
SET description = CASE code
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'RECEIVED' THEN 'Recebido'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'OVERDUE' THEN 'Atrasado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'REFUNDED' THEN 'Estornado'
  WHEN 'FAILED' THEN 'Falhou'
  WHEN 'OTHER' THEN 'Outro'
  ELSE description
END
WHERE code IN ('PENDING', 'RECEIVED', 'CONFIRMED', 'OVERDUE', 'CANCELLED', 'REFUNDED', 'FAILED', 'OTHER');

UPDATE payments
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'RECEIVED' THEN 'Recebido'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'OVERDUE' THEN 'Atrasado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'REFUNDED' THEN 'Estornado'
  WHEN 'FAILED' THEN 'Falhou'
  WHEN 'OTHER' THEN 'Outro'
  ELSE status
END
WHERE status IN ('PENDING', 'RECEIVED', 'CONFIRMED', 'OVERDUE', 'CANCELLED', 'REFUNDED', 'FAILED', 'OTHER');

DELETE FROM payment_status
WHERE code NOT IN ('PENDING', 'RECEIVED', 'CONFIRMED', 'OVERDUE', 'CANCELLED', 'REFUNDED', 'FAILED', 'OTHER');

-- notification_status
INSERT INTO notification_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('SENT', 'Enviado'),
  ('FAILED', 'Falhou')
ON CONFLICT (code) DO NOTHING;

UPDATE notification_status
SET description = CASE code
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'SENT' THEN 'Enviado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE description
END
WHERE code IN ('PENDING', 'SENT', 'FAILED');

UPDATE notifications
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'SENT' THEN 'Enviado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'SENT', 'FAILED');

DELETE FROM notification_status
WHERE code NOT IN ('PENDING', 'SENT', 'FAILED');
