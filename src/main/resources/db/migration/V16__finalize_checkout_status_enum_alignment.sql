-- Fecha o alinhamento final de checkout_status com a enum StatusCheckout.
-- O dominio suporta apenas:
--   PENDING, CONFIRMED, EXPIRED, CANCELLED, FAILED
-- Estados legados PROCESSING e PAID sao mapeados para:
--   PROCESSING -> Pendente
--   PAID       -> Confirmado

INSERT INTO checkout_status (code, description)
VALUES
  ('PENDING', 'Pendente'),
  ('CONFIRMED', 'Confirmado'),
  ('EXPIRED', 'Expirado'),
  ('CANCELLED', 'Cancelado'),
  ('FAILED', 'Falhou')
ON CONFLICT (code) DO NOTHING;

UPDATE checkout_status
SET description = CASE code
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE description
END
WHERE code IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

UPDATE checkout_intents
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Pendente'
  WHEN 'PAID' THEN 'Confirmado'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'PROCESSING', 'PAID', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

UPDATE orders
SET status = CASE status
  WHEN 'PENDING' THEN 'Pendente'
  WHEN 'PROCESSING' THEN 'Pendente'
  WHEN 'PAID' THEN 'Confirmado'
  WHEN 'CONFIRMED' THEN 'Confirmado'
  WHEN 'EXPIRED' THEN 'Expirado'
  WHEN 'CANCELLED' THEN 'Cancelado'
  WHEN 'FAILED' THEN 'Falhou'
  ELSE status
END
WHERE status IN ('PENDING', 'PROCESSING', 'PAID', 'CONFIRMED', 'EXPIRED', 'CANCELLED', 'FAILED');

DELETE FROM checkout_status
WHERE code IN ('PROCESSING', 'PAID');
