-- Alinha a tabela checkout_status com o contrato usado pela enum StatusCheckout.
-- O codigo grava descricoes em portugues via StatusCheckoutConverter.

INSERT INTO checkout_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('PROCESSING', 'Processando'),
  ('CONFIRMED', 'Confirmado'),
  ('PAID', 'Pago'),
  ('EXPIRED', 'Expirado'),
  ('CANCELLED', 'Cancelado'),
  ('FAILED', 'Falhou')
ON CONFLICT (code) DO NOTHING;

UPDATE checkout_intents
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Processando'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'PAID' THEN 'Pago'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED');

UPDATE orders
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Processando'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'PAID' THEN 'Pago'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED');

DELETE FROM checkout_status
WHERE description IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED');
