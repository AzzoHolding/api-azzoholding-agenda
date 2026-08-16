-- Fase 4 de normalizacao: extrai categorias textuais para catalogos relacionais por tenant.

DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_metrics_professional_daily;
DROP MATERIALIZED VIEW IF EXISTS mv_dashboard_service_metrics_daily;

CREATE TABLE IF NOT EXISTS service_categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS transaction_categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS product_categories (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_service_categories_tenant_name_normalized
  ON service_categories (tenant_id, lower(btrim(name)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_transaction_categories_tenant_name_normalized
  ON transaction_categories (tenant_id, lower(btrim(name)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_categories_tenant_name_normalized
  ON product_categories (tenant_id, lower(btrim(name)));

ALTER TABLE services
  ADD COLUMN IF NOT EXISTS category_id UUID;

ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS category_id UUID,
  ADD COLUMN IF NOT EXISTS product_category_id UUID;

INSERT INTO service_categories (id, tenant_id, name, created_at)
SELECT
  public.uuid_generate_v4(),
  s.tenant_id,
  btrim(s.category),
  NOW()
FROM services s
WHERE s.category IS NOT NULL
  AND btrim(s.category) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM service_categories sc
    WHERE sc.tenant_id = s.tenant_id
      AND lower(btrim(sc.name)) = lower(btrim(s.category))
  );

INSERT INTO transaction_categories (id, tenant_id, name, created_at)
SELECT
  public.uuid_generate_v4(),
  t.tenant_id,
  btrim(t.category),
  NOW()
FROM transactions t
WHERE t.category IS NOT NULL
  AND btrim(t.category) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM transaction_categories tc
    WHERE tc.tenant_id = t.tenant_id
      AND lower(btrim(tc.name)) = lower(btrim(t.category))
  );

INSERT INTO product_categories (id, tenant_id, name, created_at)
SELECT
  public.uuid_generate_v4(),
  t.tenant_id,
  btrim(t.product_category),
  NOW()
FROM transactions t
WHERE t.product_category IS NOT NULL
  AND btrim(t.product_category) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM product_categories pc
    WHERE pc.tenant_id = t.tenant_id
      AND lower(btrim(pc.name)) = lower(btrim(t.product_category))
  );

UPDATE services s
SET category_id = sc.id
FROM service_categories sc
WHERE s.category IS NOT NULL
  AND btrim(s.category) <> ''
  AND sc.tenant_id = s.tenant_id
  AND lower(btrim(sc.name)) = lower(btrim(s.category));

UPDATE transactions t
SET category_id = tc.id
FROM transaction_categories tc
WHERE t.category IS NOT NULL
  AND btrim(t.category) <> ''
  AND tc.tenant_id = t.tenant_id
  AND lower(btrim(tc.name)) = lower(btrim(t.category));

UPDATE transactions t
SET product_category_id = pc.id
FROM product_categories pc
WHERE t.product_category IS NOT NULL
  AND btrim(t.product_category) <> ''
  AND pc.tenant_id = t.tenant_id
  AND lower(btrim(pc.name)) = lower(btrim(t.product_category));

ALTER TABLE services
  ADD CONSTRAINT fk_services_category
  FOREIGN KEY (category_id) REFERENCES service_categories(id) ON DELETE SET NULL;

ALTER TABLE transactions
  ADD CONSTRAINT fk_transactions_category
  FOREIGN KEY (category_id) REFERENCES transaction_categories(id) ON DELETE RESTRICT;

ALTER TABLE transactions
  ADD CONSTRAINT fk_transactions_product_category
  FOREIGN KEY (product_category_id) REFERENCES product_categories(id) ON DELETE SET NULL;

ALTER TABLE transactions
  ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE services
  DROP COLUMN IF EXISTS category;

ALTER TABLE transactions
  DROP COLUMN IF EXISTS category,
  DROP COLUMN IF EXISTS product_category;
