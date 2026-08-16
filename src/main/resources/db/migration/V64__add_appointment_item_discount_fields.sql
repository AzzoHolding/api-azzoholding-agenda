ALTER TABLE appointment_items
  ADD COLUMN IF NOT EXISTS gross_amount BIGINT,
  ADD COLUMN IF NOT EXISTS discount_amount BIGINT;

UPDATE appointment_items
SET gross_amount = COALESCE(gross_amount, total_price),
    discount_amount = COALESCE(discount_amount, 0);

ALTER TABLE appointment_items
  ALTER COLUMN gross_amount SET NOT NULL,
  ALTER COLUMN gross_amount SET DEFAULT 0,
  ALTER COLUMN discount_amount SET NOT NULL,
  ALTER COLUMN discount_amount SET DEFAULT 0;

ALTER TABLE appointment_items
  ADD CONSTRAINT ck_appointment_items_gross_amount_non_negative
    CHECK (gross_amount >= 0),
  ADD CONSTRAINT ck_appointment_items_discount_amount_non_negative
    CHECK (discount_amount >= 0),
  ADD CONSTRAINT ck_appointment_items_discount_not_greater_than_gross
    CHECK (discount_amount <= gross_amount),
  ADD CONSTRAINT ck_appointment_items_total_matches_gross_minus_discount
    CHECK (total_price = gross_amount - discount_amount);

COMMENT ON COLUMN appointment_items.gross_amount IS 'Valor bruto do item antes de desconto, em centavos.';
COMMENT ON COLUMN appointment_items.discount_amount IS 'Desconto explicito do item, em centavos.';
