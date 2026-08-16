ALTER TABLE azzo_app.products ALTER COLUMN price_cents TYPE numeric(19, 2) USING price_cents::numeric(19, 2);
ALTER TABLE azzo_app.checkout_intents ALTER COLUMN total_price_snapshot TYPE numeric(19, 2) USING total_price_snapshot::numeric(19, 2);
ALTER TABLE azzo_app.checkout_intents ALTER COLUMN calculated_total TYPE numeric(19, 2) USING calculated_total::numeric(19, 2);
ALTER TABLE azzo_app.checkout_intents ALTER COLUMN unit_price_snapshot TYPE numeric(19, 2) USING unit_price_snapshot::numeric(19, 2);

