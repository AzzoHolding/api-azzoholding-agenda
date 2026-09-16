-- Teto de desconto da equipe no PDV (pedido do usuario: "a porcentagem do teto deve ser
-- configurada"). Achado do teste de ponta a ponta de 2026-09-16: qualquer papel aplicava 100% de
-- desconto numa comanda, zerando uma conta com um motivo digitado a mao.
--
-- 100 = SEM TETO, e o padrao de proposito: e o comportamento de antes desta migration, entao
-- nenhum salao perde no deploy o desconto que a equipe ja dava. Ligar o teto e decisao do dono,
-- em /configuracoes?tab=descontos.
--
-- O DONO nunca e limitado por este numero — quem decide dar a conta de graca e ele.

ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS pos_max_discount_percent INTEGER NOT NULL DEFAULT 100;

ALTER TABLE tenant_operational_settings
  DROP CONSTRAINT IF EXISTS chk_pos_max_discount_percent;

ALTER TABLE tenant_operational_settings
  ADD CONSTRAINT chk_pos_max_discount_percent
  CHECK (pos_max_discount_percent BETWEEN 0 AND 100);
