-- Corrige dupla conversão de centavos → centavos no cadastro de serviços.
--
-- BUG: CurrencyInput cents=true no frontend retornava centavos (ex: 6000 para R$60),
-- mas ServicoRequest.price é BigDecimal em reais e o JPA fazia toCents(6000)=600000.
-- Resultado: services.price e appointment_items ficaram 100x maiores que o correto.
--
-- CORREÇÃO: divide todos os valores monetários por 100.

-- 1. Corrige services.price
UPDATE services
SET price = price / 100
WHERE price > 0;

-- 2. Corrige appointment_items
--    A constraint ck_appointment_items_total_matches_gross_minus_discount é preservada
--    pois (x/100) = (y/100) - (z/100) se x = y - z.
--    Para discount_amount = 0, 0/100 = 0. Seguro.
ALTER TABLE appointment_items
  DROP CONSTRAINT IF EXISTS ck_appointment_items_total_matches_gross_minus_discount;

ALTER TABLE appointment_items
  DROP CONSTRAINT IF EXISTS ck_appointment_items_discount_not_greater_than_gross;

UPDATE appointment_items
SET unit_price      = unit_price / 100,
    gross_amount    = gross_amount / 100,
    discount_amount = discount_amount / 100,
    total_price     = total_price / 100
WHERE total_price > 0;

ALTER TABLE appointment_items
  ADD CONSTRAINT ck_appointment_items_discount_not_greater_than_gross
    CHECK (discount_amount <= gross_amount);

ALTER TABLE appointment_items
  ADD CONSTRAINT ck_appointment_items_total_matches_gross_minus_discount
    CHECK (total_price = gross_amount - discount_amount);

-- 3. Refresh da view de relatório para refletir valores corrigidos
REFRESH MATERIALIZED VIEW mv_relatorio_agendamentos;

UPDATE report_materialized_view_refresh_state
SET refreshed_at = CURRENT_TIMESTAMP
WHERE view_name = 'mv_relatorio_agendamentos';
