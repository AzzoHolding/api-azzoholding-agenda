-- Seed do plano trial padrao utilizado no fluxo de cadastro.
-- Necessario para ServicoAuth.ativarTrialTenant(), que busca um produto
-- ativo com is_trial = true.

INSERT INTO products (
  id,
  name,
  description,
  currency,
  price_cents,
  validity_months,
  validity_days,
  is_trial,
  highlight,
  features_json,
  active,
  priority,
  created_at,
  updated_at
)
VALUES (
  '33333333-3333-3333-3333-333333333333',
  'Plano Trial',
  'Periodo trial padrao para avaliacao inicial da plataforma.',
  'BRL',
  0,
  1,
  7,
  TRUE,
  'Trial 7 dias',
  '["Agenda","Clientes","Profissionais","Financeiro","Fiscal","Relatorios"]',
  TRUE,
  1000,
  NOW(),
  NOW()
)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  currency = EXCLUDED.currency,
  price_cents = EXCLUDED.price_cents,
  validity_months = EXCLUDED.validity_months,
  validity_days = EXCLUDED.validity_days,
  is_trial = EXCLUDED.is_trial,
  highlight = EXCLUDED.highlight,
  features_json = EXCLUDED.features_json,
  active = EXCLUDED.active,
  priority = EXCLUDED.priority,
  updated_at = NOW();

INSERT INTO product_capabilities (
  product_id,
  max_professionals,
  created_at,
  updated_at
)
VALUES (
  '33333333-3333-3333-3333-333333333333',
  3,
  NOW(),
  NOW()
)
ON CONFLICT (product_id) DO UPDATE SET
  max_professionals = EXCLUDED.max_professionals,
  updated_at = NOW();
