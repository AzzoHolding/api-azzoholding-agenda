-- UNIFIED MIGRATION (DESTRUCTIVE REBASE)
-- Generated on 2026-03-11
-- Source files in db/migration_legacy/


-- >>> BEGIN V1__structure.sql

-- === MERGED FROM V1__structure.sql ===
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "pgcrypto" WITH SCHEMA public;

DO $$
BEGIN
  IF current_schema() IS NOT NULL THEN
    EXECUTE format(
      'CREATE OR REPLACE FUNCTION %I.gen_random_uuid() RETURNS uuid AS ''SELECT public.gen_random_uuid()'' LANGUAGE sql VOLATILE',
      current_schema());
    EXECUTE format(
      'CREATE OR REPLACE FUNCTION %I.uuid_generate_v4() RETURNS uuid AS ''SELECT public.uuid_generate_v4()'' LANGUAGE sql VOLATILE',
      current_schema());
  END IF;
END $$;

CREATE TABLE tenants (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  name TEXT NOT NULL,
  slug TEXT NOT NULL UNIQUE,
  description VARCHAR(500),
  phone TEXT,
  whatsapp VARCHAR(50),
  email TEXT,
  logo TEXT,
  website VARCHAR(255),
  instagram VARCHAR(255),
  facebook VARCHAR(255),
  whatsapp_access_token_enc TEXT NOT NULL DEFAULT '',
  whatsapp_phone_number_id VARCHAR(100),
  whatsapp_business_account_id VARCHAR(100),
  whatsapp_webhook_verify_token TEXT,
  whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  plan_status_id UUID,
  asaas_customer_id VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE plan_status (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE appointment_status (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE checkout_status (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE subscription_status (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE payment_status (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE notification_status (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO plan_status (code, description)
VALUES
  ('ACTIVE', 'Ativo'),
  ('EXPIRED', 'Vencido'),
  ('SUSPENDED', 'Suspenso'),
  ('CANCELED', 'Cancelado')
ON CONFLICT (code) DO NOTHING;

INSERT INTO appointment_status (code, description)
VALUES
  ('PENDING', 'PENDING'),
  ('CONFIRMED', 'CONFIRMED'),
  ('COMPLETED', 'COMPLETED'),
  ('CANCELLED', 'CANCELLED'),
  ('NO_SHOW', 'NO_SHOW')
ON CONFLICT (code) DO NOTHING;

INSERT INTO checkout_status (code, description)
VALUES
  ('PENDING', 'PENDING'),
  ('PROCESSING', 'PROCESSING'),
  ('PAID', 'PAID'),
  ('EXPIRED', 'EXPIRED'),
  ('CANCELLED', 'CANCELLED'),
  ('FAILED', 'FAILED')
ON CONFLICT (code) DO NOTHING;

INSERT INTO subscription_status (code, description)
VALUES
  ('ACTIVE', 'ACTIVE'),
  ('TRIAL', 'TRIAL'),
  ('PAST_DUE', 'PAST_DUE'),
  ('CANCELLED', 'CANCELLED'),
  ('EXPIRED', 'EXPIRED')
ON CONFLICT (code) DO NOTHING;

INSERT INTO payment_status (code, description)
VALUES
  ('PENDING', 'PENDING'),
  ('RECEIVED', 'RECEIVED'),
  ('CONFIRMED', 'CONFIRMED'),
  ('OVERDUE', 'OVERDUE'),
  ('REFUNDED', 'REFUNDED'),
  ('CANCELLED', 'CANCELLED'),
  ('FAILED', 'FAILED')
ON CONFLICT (code) DO NOTHING;

INSERT INTO notification_status (code, description)
VALUES
  ('PENDING', 'PENDING'),
  ('SENT', 'SENT'),
  ('FAILED', 'FAILED')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE tenants
  ADD CONSTRAINT fk_tenants_plan_status
  FOREIGN KEY (plan_status_id) REFERENCES plan_status(id);

ALTER TABLE tenants
  ALTER COLUMN plan_status_id SET NOT NULL;

CREATE TABLE users (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL,
  name TEXT NOT NULL,
  email TEXT NOT NULL,
  phone TEXT,
  role TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  avatar TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT uq_users_email UNIQUE (email),
  CONSTRAINT uq_users_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE professionals (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL,
  user_id UUID,
  name TEXT NOT NULL,
  email TEXT,
  phone TEXT,
  avatar TEXT,
  specialties JSONB NOT NULL DEFAULT '[]'::jsonb,
  commission_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  working_hours JSONB NOT NULL DEFAULT '[]'::jsonb,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_professionals_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT fk_professionals_user FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id) ON DELETE SET NULL,
  CONSTRAINT uq_professionals_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE services (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  duration INTEGER NOT NULL,
  price BIGINT NOT NULL,
  category TEXT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_services_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT uq_services_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE clients (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL,
  name TEXT NOT NULL,
  email TEXT,
  phone TEXT,
  birth_date DATE,
  notes TEXT,
  total_visits INTEGER NOT NULL DEFAULT 0,
  total_spent BIGINT NOT NULL DEFAULT 0,
  last_visit DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_clients_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT uq_clients_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE appointments (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL,
  client_id UUID NOT NULL,
  professional_id UUID NOT NULL,
  service_id UUID NOT NULL,
  date DATE NOT NULL,
  start_time TEXT NOT NULL,
  end_time TEXT NOT NULL,
  status VARCHAR(100) NOT NULL,
  notes TEXT,
  total_price BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_appointments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT fk_appointments_client_tenant FOREIGN KEY (tenant_id, client_id) REFERENCES clients(tenant_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_appointments_professional_tenant FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals(tenant_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_appointments_service_tenant FOREIGN KEY (tenant_id, service_id) REFERENCES services(tenant_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_appointments_status FOREIGN KEY (status) REFERENCES appointment_status(description),
  CONSTRAINT uq_appointments_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE transactions (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL,
  appointment_id UUID,
  type TEXT NOT NULL,
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  amount BIGINT NOT NULL,
  payment_method TEXT NOT NULL,
  date TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
  CONSTRAINT fk_transactions_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL
);

CREATE TABLE service_professionals (
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
  professional_id UUID NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  PRIMARY KEY (service_id, professional_id)
);

CREATE TABLE cep_addresses (
  cep VARCHAR(8) PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  street TEXT,
  complement TEXT,
  neighborhood TEXT,
  city TEXT,
  state VARCHAR(2),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_addresses (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  street TEXT,
  number TEXT,
  complement TEXT,
  neighborhood TEXT,
  city TEXT,
  state VARCHAR(50),
  zip_code VARCHAR(20),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  code BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
  channel VARCHAR(50) NOT NULL,
  destination VARCHAR(100) NOT NULL,
  message TEXT,
  status VARCHAR(100) NOT NULL,
  error_message TEXT,
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_notifications_status FOREIGN KEY (status) REFERENCES notification_status(description)
);

CREATE TABLE conversation_state (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_identifier VARCHAR(120) NOT NULL,
  state_json TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_conversation_state_tenant_user UNIQUE (tenant_id, user_identifier)
);

CREATE TABLE menu_role_permissions (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  role VARCHAR(30) NOT NULL,
  route VARCHAR(255) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_menu_role_permissions_role_route UNIQUE (role, route)
);

CREATE TABLE roles (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE permissions (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  code VARCHAR(100) NOT NULL UNIQUE,
  description VARCHAR(255)
);

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE products (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description VARCHAR(500),
  currency VARCHAR(10) NOT NULL,
  price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
  validity_months INTEGER NOT NULL DEFAULT 1,
  highlight VARCHAR(120),
  features_json TEXT NOT NULL DEFAULT '[]',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  priority INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_products_validity_months_positive CHECK (validity_months > 0)
);

CREATE TABLE product_capabilities (
  product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
  max_professionals INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_product_capabilities_max_professionals_non_negative
    CHECK (max_professionals IS NULL OR max_professionals >= 0)
);

CREATE TABLE checkout_intents (
  id UUID PRIMARY KEY,
  tenant_id UUID,
  user_id UUID,
  product_id UUID NOT NULL,
  product_name_snapshot VARCHAR(160) NOT NULL,
  currency_snapshot VARCHAR(10) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  unit_price_snapshot BIGINT NOT NULL CHECK (unit_price_snapshot >= 0),
  quantity INT NOT NULL CHECK (quantity > 0),
  total_price_snapshot BIGINT NOT NULL CHECK (total_price_snapshot >= 0),
  calculated_total BIGINT NOT NULL CHECK (calculated_total >= 0),
  status VARCHAR(100) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  payment_reference VARCHAR(255),
  failure_reason VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  confirmed_at TIMESTAMPTZ,
  CONSTRAINT fk_checkout_intents_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_checkout_intents_status FOREIGN KEY (status) REFERENCES checkout_status(description)
);

CREATE TABLE orders (
  id UUID PRIMARY KEY,
  tenant_id UUID,
  intent_id UUID UNIQUE,
  product_id UUID NOT NULL,
  user_id UUID,
  total BIGINT NOT NULL CHECK (total >= 0),
  status VARCHAR(100) NOT NULL,
  valid_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_orders_intent FOREIGN KEY (intent_id) REFERENCES checkout_intents(id),
  CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_orders_status FOREIGN KEY (status) REFERENCES checkout_status(description)
);

CREATE TABLE subscriptions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id) ON DELETE RESTRICT,
  asaas_subscription_id VARCHAR(80) NOT NULL UNIQUE,
  asaas_customer_id VARCHAR(80) NOT NULL,
  plan_code VARCHAR(100),
  billing_type VARCHAR(30) NOT NULL,
  status VARCHAR(100) NOT NULL,
  value_cents BIGINT NOT NULL CHECK (value_cents >= 0),
  cycle VARCHAR(30) NOT NULL DEFAULT 'MONTHLY',
  next_due_date DATE,
  payment_link TEXT,
  cancelled_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_subscriptions_status FOREIGN KEY (status) REFERENCES subscription_status(description)
);

CREATE TABLE payments (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  subscription_id UUID REFERENCES subscriptions(id) ON DELETE SET NULL,
  asaas_payment_id VARCHAR(80) NOT NULL UNIQUE,
  asaas_subscription_id VARCHAR(80),
  status VARCHAR(100) NOT NULL,
  billing_type VARCHAR(30),
  amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0),
  net_amount_cents BIGINT,
  due_date DATE,
  reference_month VARCHAR(7),
  paid_at TIMESTAMPTZ,
  invoice_url TEXT,
  bank_slip_url TEXT,
  boleto_identification_field TEXT,
  boleto_bar_code TEXT,
  boleto_nosso_numero VARCHAR(100),
  pix_qr_code TEXT,
  pix_payload TEXT,
  expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_payments_status FOREIGN KEY (status) REFERENCES payment_status(description)
);

CREATE TABLE webhook_event_logs (
  id UUID PRIMARY KEY,
  provider VARCHAR(50) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  event_type VARCHAR(80) NOT NULL,
  tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
  external_subscription_id VARCHAR(80),
  external_payment_id VARCHAR(80),
  payload_hash VARCHAR(100) NOT NULL,
  payload_json TEXT NOT NULL,
  processed BOOLEAN NOT NULL DEFAULT FALSE,
  error_message VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMPTZ
);

CREATE TABLE integration_logs (
  id UUID PRIMARY KEY,
  provider VARCHAR(50) NOT NULL,
  direction VARCHAR(20) NOT NULL,
  action VARCHAR(80) NOT NULL,
  tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
  external_reference VARCHAR(120),
  request_payload TEXT,
  response_payload TEXT,
  http_status INT,
  success BOOLEAN NOT NULL,
  error_message VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE specialties (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE professional_specialties (
  professional_id UUID NOT NULL REFERENCES professionals(id) ON DELETE CASCADE,
  specialty_id UUID NOT NULL REFERENCES specialties(id) ON DELETE CASCADE,
  PRIMARY KEY (professional_id, specialty_id)
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  token_hash VARCHAR(120) NOT NULL UNIQUE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  replaced_by_token_hash VARCHAR(120),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_tenants_plan_status_id ON tenants(plan_status_id);
CREATE INDEX idx_professionals_tenant_id ON professionals(tenant_id);
CREATE INDEX idx_professionals_user_id ON professionals(user_id);
CREATE INDEX idx_services_tenant_id ON services(tenant_id);
CREATE INDEX idx_clients_tenant_id ON clients(tenant_id);
CREATE INDEX idx_clients_tenant_phone ON clients(tenant_id, phone);
CREATE INDEX idx_appointments_tenant_id ON appointments(tenant_id);
CREATE INDEX idx_appointments_client_id ON appointments(client_id);
CREATE INDEX idx_appointments_professional_id ON appointments(professional_id);
CREATE INDEX idx_appointments_service_id ON appointments(service_id);
CREATE INDEX idx_appointments_slot ON appointments(tenant_id, professional_id, date, start_time);
CREATE INDEX idx_transactions_tenant_id ON transactions(tenant_id);
CREATE INDEX idx_transactions_appointment_id ON transactions(appointment_id);
CREATE INDEX idx_transactions_tenant_date ON transactions(tenant_id, date);
CREATE INDEX idx_notifications_tenant_id ON notifications(tenant_id);
CREATE INDEX idx_notifications_appointment_id ON notifications(appointment_id);
CREATE INDEX idx_notifications_tenant_created_at ON notifications(tenant_id, created_at);
CREATE INDEX idx_conversation_state_updated_at ON conversation_state(updated_at);
CREATE INDEX idx_menu_role_permissions_role_active ON menu_role_permissions (role, is_active);
CREATE INDEX idx_service_professionals_professional_id ON service_professionals(professional_id);
CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_products_active_priority ON products(active, priority DESC);
CREATE INDEX idx_checkout_intents_tenant_status ON checkout_intents(tenant_id, status);
CREATE INDEX idx_checkout_intents_status_expires_at ON checkout_intents(status, expires_at);
CREATE INDEX idx_checkout_intents_user_id ON checkout_intents(user_id);
CREATE INDEX idx_orders_tenant_created_at ON orders(tenant_id, created_at DESC);
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_intent_id ON orders(intent_id);
CREATE INDEX idx_orders_tenant_status_valid_until ON orders(tenant_id, status, valid_until DESC);
CREATE INDEX idx_subscriptions_tenant_status ON subscriptions(tenant_id, status);
CREATE INDEX idx_subscriptions_asaas_customer ON subscriptions(asaas_customer_id);
CREATE INDEX idx_payments_tenant_status ON payments(tenant_id, status);
CREATE INDEX idx_payments_subscription_id ON payments(subscription_id);
CREATE INDEX idx_payments_asaas_subscription ON payments(asaas_subscription_id);
CREATE INDEX idx_payments_expires_at ON payments(expires_at);
CREATE INDEX idx_payments_reference_month ON payments(reference_month);
CREATE INDEX idx_webhook_event_logs_tenant_created ON webhook_event_logs(tenant_id, created_at DESC);
CREATE INDEX idx_webhook_event_logs_external_payment ON webhook_event_logs(external_payment_id);
CREATE INDEX idx_integration_logs_provider_created ON integration_logs(provider, created_at DESC);
CREATE INDEX idx_integration_logs_tenant_created ON integration_logs(tenant_id, created_at DESC);
CREATE UNIQUE INDEX uq_specialties_tenant_name_ci ON specialties (tenant_id, lower(name));
CREATE INDEX idx_specialties_tenant_name ON specialties (tenant_id, name);
CREATE INDEX idx_professional_specialties_specialty_id ON professional_specialties (specialty_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_tenant_id ON refresh_tokens(tenant_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

CREATE UNIQUE INDEX uq_notifications_appointment_channel_destination
  ON notifications(appointment_id, channel, destination);

CREATE UNIQUE INDEX uq_tenants_whatsapp_phone_number_id
  ON tenants(whatsapp_phone_number_id)
  WHERE whatsapp_phone_number_id IS NOT NULL;

CREATE UNIQUE INDEX uq_tenants_asaas_customer_id
  ON tenants(asaas_customer_id)
  WHERE asaas_customer_id IS NOT NULL;

COMMENT ON TABLE tenants IS 'Cadastro dos tenants (saloes) do sistema SaaS multi-tenant.';
COMMENT ON COLUMN tenants.plan_status_id IS 'Status atual do plano/licenca do tenant.';
COMMENT ON TABLE users IS 'Usuarios do sistema vinculados a um tenant (owner, profissional, cliente).';
COMMENT ON TABLE professionals IS 'Profissionais que atendem no salao e seus dados operacionais.';
COMMENT ON TABLE services IS 'Servicos oferecidos pelo tenant, com duracao, preco e status.';
COMMENT ON TABLE clients IS 'Clientes finais do salao e seu historico basico.';
COMMENT ON TABLE appointments IS 'Agendamentos de servicos entre clientes e profissionais.';
COMMENT ON TABLE transactions IS 'Lancamentos financeiros (receitas/despesas) do tenant.';
COMMENT ON TABLE service_professionals IS 'Tabela de relacionamento N:N entre servicos e profissionais.';
COMMENT ON TABLE cep_addresses IS 'Cache/local store de enderecos consultados por CEP.';
COMMENT ON TABLE tenant_addresses IS 'Endereco principal de cada tenant.';
COMMENT ON TABLE notifications IS 'Log de notificacoes enviadas por canal (ex.: WhatsApp).';
COMMENT ON TABLE conversation_state IS 'Estado persistido da conversa do modulo assistant por tenant e usuario.';
COMMENT ON TABLE menu_role_permissions IS 'Permissoes de rotas de menu globais por perfil de usuario.';
COMMENT ON TABLE roles IS 'Papeis de acesso por tenant para controle de autorizacao.';
COMMENT ON TABLE permissions IS 'Permissoes atomicas do sistema para RBAC.';
COMMENT ON TABLE role_permissions IS 'Relacionamento entre papeis e permissoes.';
COMMENT ON TABLE user_roles IS 'Relacionamento entre usuarios e papeis.';
COMMENT ON TABLE products IS 'Catalogo de produtos/planos vendidos no checkout.';
COMMENT ON TABLE product_capabilities IS 'Regras/limites por produto/plano (ex.: maximo de profissionais).';
COMMENT ON TABLE checkout_intents IS 'Intencoes de checkout criadas antes da confirmacao de compra.';
COMMENT ON TABLE orders IS 'Pedidos confirmados originados de intents de checkout.';
COMMENT ON TABLE subscriptions IS 'Assinaturas recorrentes de licenca SaaS vinculadas ao Asaas.';
COMMENT ON TABLE payments IS 'Pagamentos/cobrancas de assinaturas sincronizados com Asaas.';
COMMENT ON TABLE webhook_event_logs IS 'Log de eventos recebidos por webhook com controle de idempotencia.';
COMMENT ON TABLE integration_logs IS 'Auditoria tecnica de integracoes externas (request/response/erro).';
COMMENT ON TABLE specialties IS 'Catalogo de especialidades por tenant (ex.: Corte, Coloracao).';
COMMENT ON TABLE professional_specialties IS 'Relacionamento N:N entre profissionais e especialidades.';
COMMENT ON TABLE refresh_tokens IS 'Refresh tokens persistidos com hash para renovacao segura de sessoes.';

CREATE MATERIALIZED VIEW mv_dashboard_metrics_daily
AS
WITH today_ctx AS (
  SELECT (NOW() AT TIME ZONE 'America/Sao_Paulo')::date AS today
),
transaction_daily AS (
  SELECT
    t.tenant_id,
    (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    COALESCE(SUM(t.amount) FILTER (WHERE t.type = 'INCOME'), 0)::bigint AS today_revenue
  FROM transactions t
  GROUP BY t.tenant_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
),
appointment_daily AS (
  SELECT
    a.tenant_id,
    a.date AS metric_date,
    COUNT(*)::int AS today_appointments,
    COUNT(*) FILTER (WHERE a.status = 'Pendente')::int AS pending_appointments,
    COUNT(*) FILTER (WHERE a.status = 'Concluido')::int AS completed_today
  FROM appointments a
  GROUP BY a.tenant_id, a.date
),
client_totals AS (
  SELECT
    c.tenant_id,
    COUNT(*)::int AS total_clients
  FROM clients c
  GROUP BY c.tenant_id
),
activity_dates AS (
  SELECT tenant_id, metric_date FROM transaction_daily
  UNION
  SELECT tenant_id, metric_date FROM appointment_daily
),
tenant_dates AS (
  SELECT tenant_id, metric_date FROM activity_dates
  UNION
  SELECT t.id AS tenant_id, ctx.today AS metric_date
  FROM tenants t
  CROSS JOIN today_ctx ctx
)
SELECT
  td.tenant_id,
  td.metric_date,
  COALESCE(tx.today_revenue, 0)::bigint AS today_revenue,
  COALESCE(ap.today_appointments, 0)::int AS today_appointments,
  COALESCE(ap.pending_appointments, 0)::int AS pending_appointments,
  COALESCE(ap.completed_today, 0)::int AS completed_today,
  COALESCE(ct.total_clients, 0)::int AS total_clients
FROM tenant_dates td
LEFT JOIN transaction_daily tx
  ON tx.tenant_id = td.tenant_id
 AND tx.metric_date = td.metric_date
LEFT JOIN appointment_daily ap
  ON ap.tenant_id = td.tenant_id
 AND ap.metric_date = td.metric_date
LEFT JOIN client_totals ct
  ON ct.tenant_id = td.tenant_id
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_dashboard_metrics_daily_tenant_date
  ON mv_dashboard_metrics_daily (tenant_id, metric_date);

CREATE INDEX idx_mv_dashboard_metrics_daily_date
  ON mv_dashboard_metrics_daily (metric_date);

CREATE INDEX idx_mv_dashboard_metrics_daily_tenant_date
  ON mv_dashboard_metrics_daily (tenant_id, metric_date DESC);

CREATE INDEX idx_transactions_tenant_income_local_day
  ON transactions (tenant_id, ((date AT TIME ZONE 'America/Sao_Paulo')::date))
  WHERE type = 'INCOME';

CREATE INDEX idx_appointments_tenant_date_status
  ON appointments (tenant_id, date, status);

REFRESH MATERIALIZED VIEW mv_dashboard_metrics_daily;

CREATE MATERIALIZED VIEW mv_revenue_daily
AS
SELECT
  t.tenant_id,
  (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
  SUM(t.amount)::bigint AS revenue
FROM transactions t
WHERE t.type = 'INCOME'
GROUP BY t.tenant_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_revenue_daily_tenant_date
  ON mv_revenue_daily (tenant_id, metric_date);

CREATE INDEX idx_mv_revenue_daily_metric_date
  ON mv_revenue_daily (metric_date);

CREATE INDEX idx_mv_revenue_daily_tenant_date
  ON mv_revenue_daily (tenant_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_revenue_daily;

-- === MERGED FROM V3__notifications_keyset_index.sql ===
CREATE INDEX IF NOT EXISTS idx_notifications_tenant_created_at_id_desc
  ON notifications (tenant_id, created_at DESC, id DESC);

-- === MERGED FROM V4__tenant_whatsapp_permissions.sql ===
CREATE TABLE tenant_whatsapp_permissions (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  can_schedule BOOLEAN NOT NULL DEFAULT TRUE,
  can_cancel BOOLEAN NOT NULL DEFAULT TRUE,
  can_reschedule BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO tenant_whatsapp_permissions (tenant_id, can_schedule, can_cancel, can_reschedule, created_at, updated_at)
SELECT t.id, TRUE, TRUE, TRUE, NOW(), NOW()
FROM tenants t
WHERE NOT EXISTS (
  SELECT 1
  FROM tenant_whatsapp_permissions p
  WHERE p.tenant_id = t.id
);

COMMENT ON TABLE tenant_whatsapp_permissions IS
  'Permissoes por tenant para operacoes de agenda via WhatsApp (agendar, cancelar, remarcar).';

-- === MERGED FROM V5__tenant_whatsapp_config_full.sql ===
CREATE TABLE IF NOT EXISTS tenant_whatsapp_config (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  whatsapp_access_token_enc TEXT NOT NULL DEFAULT '',
  whatsapp_phone_number_id VARCHAR(100),
  whatsapp_business_account_id VARCHAR(100),
  whatsapp_webhook_verify_token TEXT,
  whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  can_schedule BOOLEAN NOT NULL DEFAULT TRUE,
  can_cancel BOOLEAN NOT NULL DEFAULT TRUE,
  can_reschedule BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO tenant_whatsapp_config (
  tenant_id,
  whatsapp_access_token_enc,
  whatsapp_phone_number_id,
  whatsapp_business_account_id,
  whatsapp_webhook_verify_token,
  whatsapp_enabled,
  can_schedule,
  can_cancel,
  can_reschedule,
  created_at,
  updated_at
)
SELECT
  t.id,
  COALESCE(t.whatsapp_access_token_enc, ''),
  t.whatsapp_phone_number_id,
  t.whatsapp_business_account_id,
  t.whatsapp_webhook_verify_token,
  COALESCE(t.whatsapp_enabled, FALSE),
  COALESCE(p.can_schedule, TRUE),
  COALESCE(p.can_cancel, TRUE),
  COALESCE(p.can_reschedule, TRUE),
  NOW(),
  NOW()
FROM tenants t
LEFT JOIN tenant_whatsapp_permissions p ON p.tenant_id = t.id
ON CONFLICT (tenant_id) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_whatsapp_config_phone_number_id
  ON tenant_whatsapp_config (whatsapp_phone_number_id)
  WHERE whatsapp_phone_number_id IS NOT NULL;

DROP INDEX IF EXISTS uq_tenants_whatsapp_phone_number_id;

ALTER TABLE tenants DROP COLUMN IF EXISTS whatsapp_access_token_enc;
ALTER TABLE tenants DROP COLUMN IF EXISTS whatsapp_phone_number_id;
ALTER TABLE tenants DROP COLUMN IF EXISTS whatsapp_business_account_id;
ALTER TABLE tenants DROP COLUMN IF EXISTS whatsapp_webhook_verify_token;
ALTER TABLE tenants DROP COLUMN IF EXISTS whatsapp_enabled;

DROP TABLE IF EXISTS tenant_whatsapp_permissions;

COMMENT ON TABLE tenant_whatsapp_config IS
  'Configuracao completa do WhatsApp por tenant (credenciais, status e permissoes operacionais).';

-- === MERGED FROM V6__tenant_whatsapp_verify_token_hash.sql ===
ALTER TABLE tenant_whatsapp_config
  ADD COLUMN IF NOT EXISTS whatsapp_webhook_verify_token_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_tenant_whatsapp_verify_token_hash
  ON tenant_whatsapp_config (whatsapp_webhook_verify_token_hash);

-- === MERGED FROM V7__tenant_trial_document_hash.sql ===
ALTER TABLE tenants
  ADD COLUMN IF NOT EXISTS trial_document_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenants_trial_document_hash
  ON tenants (trial_document_hash)
  WHERE trial_document_hash IS NOT NULL;

-- === MERGED FROM V8__products_trial_flag_and_validity_days.sql ===
ALTER TABLE products
  ADD COLUMN IF NOT EXISTS validity_days INTEGER;

ALTER TABLE products
  ADD COLUMN IF NOT EXISTS is_trial BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE products
SET validity_days = CASE
    WHEN id = '33333333-3333-3333-3333-333333333333' THEN 7
    ELSE GREATEST(1, COALESCE(validity_months, 1) * 30)
  END
WHERE validity_days IS NULL OR validity_days <= 0;

UPDATE products
SET is_trial = TRUE
WHERE id = '33333333-3333-3333-3333-333333333333';

UPDATE products
SET active = TRUE
WHERE id = '33333333-3333-3333-3333-333333333333';

CREATE INDEX IF NOT EXISTS idx_products_active_not_trial_priority
  ON products (active, is_trial, priority DESC);

-- === MERGED FROM V9__dashboard_metrics_by_professional_mv.sql ===
CREATE MATERIALIZED VIEW mv_dashboard_metrics_professional_daily
AS
WITH revenue_daily AS (
  SELECT
    t.tenant_id,
    a.professional_id,
    (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    COALESCE(SUM(t.amount), 0)::bigint AS revenue
  FROM transactions t
  JOIN appointments a ON a.id = t.appointment_id
  WHERE t.type = 'INCOME'
    AND t.category = 'APPOINTMENT'
  GROUP BY t.tenant_id, a.professional_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
),
commission_daily AS (
  SELECT
    t.tenant_id,
    a.professional_id,
    (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    COALESCE(SUM(t.amount), 0)::bigint AS commission
  FROM transactions t
  JOIN appointments a ON a.id = t.appointment_id
  WHERE t.type = 'EXPENSE'
    AND t.category = 'COMMISSION'
  GROUP BY t.tenant_id, a.professional_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
),
appointments_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    a.date AS metric_date,
    COUNT(*) FILTER (WHERE a.status = 'Concluido')::int AS completed_services,
    COUNT(DISTINCT a.client_id) FILTER (WHERE a.status = 'Concluido')::int AS clients_served
  FROM appointments a
  GROUP BY a.tenant_id, a.professional_id, a.date
),
activity_dates AS (
  SELECT tenant_id, professional_id, metric_date FROM revenue_daily
  UNION
  SELECT tenant_id, professional_id, metric_date FROM commission_daily
  UNION
  SELECT tenant_id, professional_id, metric_date FROM appointments_daily
)
SELECT
  ad.tenant_id,
  ad.professional_id,
  ad.metric_date,
  COALESCE(rd.revenue, 0)::bigint AS revenue,
  COALESCE(cd.commission, 0)::bigint AS commission,
  COALESCE(ap.completed_services, 0)::int AS completed_services,
  COALESCE(ap.clients_served, 0)::int AS clients_served
FROM activity_dates ad
LEFT JOIN revenue_daily rd
  ON rd.tenant_id = ad.tenant_id
 AND rd.professional_id = ad.professional_id
 AND rd.metric_date = ad.metric_date
LEFT JOIN commission_daily cd
  ON cd.tenant_id = ad.tenant_id
 AND cd.professional_id = ad.professional_id
 AND cd.metric_date = ad.metric_date
LEFT JOIN appointments_daily ap
  ON ap.tenant_id = ad.tenant_id
 AND ap.professional_id = ad.professional_id
 AND ap.metric_date = ad.metric_date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_dashboard_metrics_prof_daily_tenant_prof_date
  ON mv_dashboard_metrics_professional_daily (tenant_id, professional_id, metric_date);

CREATE INDEX idx_mv_dashboard_metrics_prof_daily_tenant_date
  ON mv_dashboard_metrics_professional_daily (tenant_id, metric_date DESC);

CREATE INDEX idx_mv_dashboard_metrics_prof_daily_tenant_prof_date
  ON mv_dashboard_metrics_professional_daily (tenant_id, professional_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_dashboard_metrics_professional_daily;

-- === MERGED FROM V10__dashboard_service_metrics_mv.sql ===
CREATE MATERIALIZED VIEW mv_dashboard_service_metrics_daily
AS
WITH appointments_daily AS (
  SELECT
    a.tenant_id,
    a.professional_id,
    a.service_id,
    a.date AS metric_date,
    COUNT(*)::int AS total_appointments,
    COUNT(*) FILTER (WHERE a.status = 'Concluido')::int AS completed_appointments,
    COUNT(*) FILTER (WHERE a.status = 'Cancelado')::int AS canceled_appointments
  FROM appointments a
  GROUP BY a.tenant_id, a.professional_id, a.service_id, a.date
),
revenue_daily AS (
  SELECT
    t.tenant_id,
    a.professional_id,
    a.service_id,
    (t.date AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    COALESCE(SUM(t.amount), 0)::bigint AS revenue
  FROM transactions t
  JOIN appointments a ON a.id = t.appointment_id
  WHERE t.type = 'INCOME'
    AND t.category = 'APPOINTMENT'
  GROUP BY t.tenant_id, a.professional_id, a.service_id, (t.date AT TIME ZONE 'America/Sao_Paulo')::date
),
activity_dates AS (
  SELECT tenant_id, professional_id, service_id, metric_date FROM appointments_daily
  UNION
  SELECT tenant_id, professional_id, service_id, metric_date FROM revenue_daily
)
SELECT
  ad.tenant_id,
  ad.professional_id,
  ad.service_id,
  ad.metric_date,
  COALESCE(ap.total_appointments, 0)::int AS total_appointments,
  COALESCE(ap.completed_appointments, 0)::int AS completed_appointments,
  COALESCE(ap.canceled_appointments, 0)::int AS canceled_appointments,
  COALESCE(rv.revenue, 0)::bigint AS revenue
FROM activity_dates ad
LEFT JOIN appointments_daily ap
  ON ap.tenant_id = ad.tenant_id
 AND ap.professional_id = ad.professional_id
 AND ap.service_id = ad.service_id
 AND ap.metric_date = ad.metric_date
LEFT JOIN revenue_daily rv
  ON rv.tenant_id = ad.tenant_id
 AND rv.professional_id = ad.professional_id
 AND rv.service_id = ad.service_id
 AND rv.metric_date = ad.metric_date
WITH NO DATA;

CREATE UNIQUE INDEX uq_mv_dashboard_service_metrics_daily_tenant_prof_service_date
  ON mv_dashboard_service_metrics_daily (tenant_id, professional_id, service_id, metric_date);

CREATE INDEX idx_mv_dashboard_service_metrics_daily_tenant_date
  ON mv_dashboard_service_metrics_daily (tenant_id, metric_date DESC);

CREATE INDEX idx_mv_dashboard_service_metrics_daily_tenant_prof_date
  ON mv_dashboard_service_metrics_daily (tenant_id, professional_id, metric_date DESC);

CREATE INDEX idx_mv_dashboard_service_metrics_daily_tenant_service_date
  ON mv_dashboard_service_metrics_daily (tenant_id, service_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_dashboard_service_metrics_daily;

-- === MERGED FROM V11__permission_override_audit_tables.sql ===
CREATE TABLE item_menu (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  route VARCHAR(255) NOT NULL UNIQUE,
  label VARCHAR(120) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissao_menu_perfil (
  role_id UUID NOT NULL REFERENCES roles(id) ON UPDATE CASCADE ON DELETE CASCADE,
  item_menu_id UUID NOT NULL REFERENCES item_menu(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  can_view BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (role_id, item_menu_id)
);

CREATE TABLE sobreposicao_perfil_menu_empresa (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON UPDATE CASCADE ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON UPDATE CASCADE ON DELETE CASCADE,
  item_menu_id UUID NOT NULL REFERENCES item_menu(id) ON UPDATE CASCADE ON DELETE RESTRICT,
  enabled BOOLEAN NOT NULL,
  reason TEXT,
  updated_by UUID REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_sobreposicao_tenant_role_item UNIQUE (tenant_id, role_id, item_menu_id)
);

CREATE TABLE auditoria_permissao (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON UPDATE CASCADE ON DELETE CASCADE,
  role_id UUID REFERENCES roles(id) ON UPDATE CASCADE ON DELETE SET NULL,
  item_menu_id UUID REFERENCES item_menu(id) ON UPDATE CASCADE ON DELETE SET NULL,
  changed_by UUID REFERENCES users(id) ON UPDATE CASCADE ON DELETE SET NULL,
  action VARCHAR(50) NOT NULL,
  before_data JSONB,
  after_data JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permissao_menu_perfil_role ON permissao_menu_perfil (role_id);
CREATE INDEX idx_permissao_menu_perfil_item ON permissao_menu_perfil (item_menu_id);
CREATE INDEX idx_sobreposicao_tenant ON sobreposicao_perfil_menu_empresa (tenant_id);
CREATE INDEX idx_sobreposicao_role_item ON sobreposicao_perfil_menu_empresa (role_id, item_menu_id);
CREATE INDEX idx_auditoria_permissao_tenant_created_at ON auditoria_permissao (tenant_id, created_at DESC);

-- === MERGED FROM V12__fk_integrity_rules.sql ===
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_tenant;
ALTER TABLE users
  ADD CONSTRAINT fk_users_tenant
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE professionals DROP CONSTRAINT IF EXISTS fk_professionals_tenant;
ALTER TABLE professionals
  ADD CONSTRAINT fk_professionals_tenant
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE professionals DROP CONSTRAINT IF EXISTS fk_professionals_user;
ALTER TABLE professionals
  ADD CONSTRAINT fk_professionals_user
  FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE services DROP CONSTRAINT IF EXISTS fk_services_tenant;
ALTER TABLE services
  ADD CONSTRAINT fk_services_tenant
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE clients DROP CONSTRAINT IF EXISTS fk_clients_tenant;
ALTER TABLE clients
  ADD CONSTRAINT fk_clients_tenant
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS fk_appointments_tenant;
ALTER TABLE appointments
  ADD CONSTRAINT fk_appointments_tenant
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS fk_appointments_client_tenant;
ALTER TABLE appointments
  ADD CONSTRAINT fk_appointments_client_tenant
  FOREIGN KEY (tenant_id, client_id) REFERENCES clients(tenant_id, id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS fk_appointments_professional_tenant;
ALTER TABLE appointments
  ADD CONSTRAINT fk_appointments_professional_tenant
  FOREIGN KEY (tenant_id, professional_id) REFERENCES professionals(tenant_id, id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS fk_appointments_service_tenant;
ALTER TABLE appointments
  ADD CONSTRAINT fk_appointments_service_tenant
  FOREIGN KEY (tenant_id, service_id) REFERENCES services(tenant_id, id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS fk_transactions_tenant;
ALTER TABLE transactions
  ADD CONSTRAINT fk_transactions_tenant
  FOREIGN KEY (tenant_id) REFERENCES tenants(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS fk_transactions_appointment;
ALTER TABLE transactions
  ADD CONSTRAINT fk_transactions_appointment
  FOREIGN KEY (appointment_id) REFERENCES appointments(id)
  ON UPDATE CASCADE ON DELETE RESTRICT;

-- === MERGED FROM V13__fiscal_provider_real_persistence.sql ===
CREATE TABLE fiscal_tax_configs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  regime VARCHAR(60),
  icms_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  pis_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  cofins_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_fiscal_tax_configs_tenant UNIQUE (tenant_id)
);

CREATE TABLE fiscal_invoices (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  external_invoice_id VARCHAR(120) NOT NULL,
  appointment_id UUID,
  invoice_type VARCHAR(30),
  status VARCHAR(40),
  numero_nf VARCHAR(60),
  serie_nf VARCHAR(30),
  chave_acesso VARCHAR(80),
  protocolo_autorizacao VARCHAR(120),
  data_emissao DATE,
  total_amount BIGINT NOT NULL DEFAULT 0,
  payload_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_fiscal_invoices_tenant_external UNIQUE (tenant_id, external_invoice_id)
);

CREATE INDEX idx_fiscal_invoices_tenant_status ON fiscal_invoices (tenant_id, status);
CREATE INDEX idx_fiscal_invoices_tenant_data_emissao ON fiscal_invoices (tenant_id, data_emissao);
CREATE INDEX idx_fiscal_invoices_tenant_chave_acesso ON fiscal_invoices (tenant_id, chave_acesso);
CREATE INDEX idx_fiscal_invoices_tenant_numero_serie_tipo ON fiscal_invoices (tenant_id, numero_nf, serie_nf, invoice_type);

CREATE TABLE fiscal_apuracao_monthly (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  ano INT NOT NULL,
  mes INT NOT NULL,
  total_servicos BIGINT NOT NULL DEFAULT 0,
  total_impostos BIGINT NOT NULL DEFAULT 0,
  total_documentos BIGINT NOT NULL DEFAULT 0,
  documentos_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_fiscal_apuracao_monthly_tenant_ano_mes UNIQUE (tenant_id, ano, mes)
);

CREATE INDEX idx_fiscal_apuracao_monthly_tenant_ano_mes ON fiscal_apuracao_monthly (tenant_id, ano, mes);

-- === MERGED FROM V14__auditoria_e_termos_append_only.sql ===
CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
  actor_user_id UUID,
  actor_role VARCHAR(50),
  module VARCHAR(50) NOT NULL,
  action VARCHAR(120) NOT NULL,
  entity_type VARCHAR(80),
  entity_id VARCHAR(120),
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(80),
  error_message VARCHAR(500),
  request_id VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(200),
  source_channel VARCHAR(30) NOT NULL,
  ip_address VARCHAR(64),
  user_agent VARCHAR(300),
  before_json TEXT,
  after_json TEXT,
  metadata_json TEXT,
  has_changes BOOLEAN NOT NULL DEFAULT FALSE,
  changed_fields_json TEXT,
  event_hash VARCHAR(128) NOT NULL,
  prev_event_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_retention_events (
  id UUID PRIMARY KEY,
  tenant_id UUID REFERENCES tenants(id) ON DELETE SET NULL,
  policy_version VARCHAR(40) NOT NULL,
  retention_period_days INT NOT NULL,
  window_start TIMESTAMPTZ NOT NULL,
  window_end TIMESTAMPTZ NOT NULL,
  affected_rows BIGINT NOT NULL,
  executed_by VARCHAR(80) NOT NULL,
  execution_id VARCHAR(100) NOT NULL,
  evidence_hash VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE terms_versions (
  id UUID PRIMARY KEY,
  document_type VARCHAR(40) NOT NULL,
  version VARCHAR(40) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  published_by UUID,
  CONSTRAINT uq_terms_versions_doc_type_version UNIQUE (document_type, version)
);

CREATE TABLE terms_acceptances (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  terms_version_id UUID NOT NULL REFERENCES terms_versions(id) ON DELETE RESTRICT,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_address VARCHAR(64),
  request_id VARCHAR(100) NOT NULL,
  acceptance_hash VARCHAR(128) NOT NULL
);

CREATE TABLE terms_lifecycle_events (
  id UUID PRIMARY KEY,
  terms_version_id UUID NOT NULL REFERENCES terms_versions(id) ON DELETE RESTRICT,
  event_type VARCHAR(30) NOT NULL,
  event_metadata_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by UUID
);

CREATE INDEX idx_audit_events_tenant_created_at ON audit_events (tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_audit_events_tenant_module_created_at ON audit_events (tenant_id, module, created_at DESC, id DESC);
CREATE INDEX idx_audit_events_tenant_entity ON audit_events (tenant_id, entity_type, entity_id, created_at DESC, id DESC);
CREATE INDEX idx_audit_events_request_id ON audit_events (request_id);
CREATE INDEX idx_audit_events_status_created_at ON audit_events (status, created_at DESC);
CREATE INDEX idx_audit_events_action_created_at ON audit_events (action, created_at DESC);

CREATE INDEX idx_audit_retention_events_created_at ON audit_retention_events (created_at DESC);
CREATE INDEX idx_audit_retention_events_execution_id ON audit_retention_events (execution_id);

CREATE INDEX idx_terms_acceptances_tenant_user_accepted_at ON terms_acceptances (tenant_id, user_id, accepted_at DESC);
CREATE INDEX idx_terms_acceptances_terms_version ON terms_acceptances (terms_version_id);
CREATE INDEX idx_terms_lifecycle_events_terms_version_created_at ON terms_lifecycle_events (terms_version_id, created_at DESC);

-- Defesa em profundidade: por padrao a tabela e append-only para usuarios comuns.
REVOKE UPDATE, DELETE ON audit_events FROM PUBLIC;
REVOKE UPDATE, DELETE ON audit_retention_events FROM PUBLIC;
REVOKE UPDATE, DELETE ON terms_versions FROM PUBLIC;
REVOKE UPDATE, DELETE ON terms_acceptances FROM PUBLIC;
REVOKE UPDATE, DELETE ON terms_lifecycle_events FROM PUBLIC;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
    EXECUTE 'REVOKE UPDATE, DELETE ON audit_events FROM app_user';
    EXECUTE 'REVOKE UPDATE, DELETE ON audit_retention_events FROM app_user';
    EXECUTE 'REVOKE UPDATE, DELETE ON terms_versions FROM app_user';
    EXECUTE 'REVOKE UPDATE, DELETE ON terms_acceptances FROM app_user';
    EXECUTE 'REVOKE UPDATE, DELETE ON terms_lifecycle_events FROM app_user';
  END IF;
END $$;

CREATE OR REPLACE FUNCTION deny_append_only_mutation()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'append-only table: mutation blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_deny_audit_events_mutation
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW
EXECUTE FUNCTION deny_append_only_mutation();

CREATE TRIGGER trg_deny_audit_retention_events_mutation
BEFORE UPDATE OR DELETE ON audit_retention_events
FOR EACH ROW
EXECUTE FUNCTION deny_append_only_mutation();

CREATE TRIGGER trg_deny_terms_versions_mutation
BEFORE UPDATE OR DELETE ON terms_versions
FOR EACH ROW
EXECUTE FUNCTION deny_append_only_mutation();

CREATE TRIGGER trg_deny_terms_acceptances_mutation
BEFORE UPDATE OR DELETE ON terms_acceptances
FOR EACH ROW
EXECUTE FUNCTION deny_append_only_mutation();

CREATE TRIGGER trg_deny_terms_lifecycle_events_mutation
BEFORE UPDATE OR DELETE ON terms_lifecycle_events
FOR EACH ROW
EXECUTE FUNCTION deny_append_only_mutation();

-- === MERGED FROM V17__roles_remove_tenant_id.sql ===
-- Converte RBAC para roles globais (sem tenant_id em roles).
-- Preserva relacionamentos em user_roles/role_permissions e tabelas de override/auditoria.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'roles' AND column_name = 'tenant_id'
  ) THEN

    CREATE TEMP TABLE tmp_role_map (
      old_role_id UUID PRIMARY KEY,
      new_role_id UUID NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO tmp_role_map (old_role_id, new_role_id)
    SELECT
      r.id AS old_role_id,
      c.canonical_id AS new_role_id
    FROM roles r
    JOIN (
      SELECT
        UPPER(name) AS role_name,
        (ARRAY_AGG(id ORDER BY id))[1] AS canonical_id
      FROM roles
      GROUP BY UPPER(name)
    ) c
      ON UPPER(r.name) = c.role_name;

    CREATE TEMP TABLE tmp_user_roles_new ON COMMIT DROP AS
    SELECT DISTINCT
      ur.user_id,
      rm.new_role_id AS role_id
    FROM user_roles ur
    JOIN tmp_role_map rm ON rm.old_role_id = ur.role_id;

    TRUNCATE TABLE user_roles;
    INSERT INTO user_roles (user_id, role_id)
    SELECT user_id, role_id
    FROM tmp_user_roles_new;

    CREATE TEMP TABLE tmp_role_permissions_new ON COMMIT DROP AS
    SELECT DISTINCT
      rm.new_role_id AS role_id,
      rp.permission_id
    FROM role_permissions rp
    JOIN tmp_role_map rm ON rm.old_role_id = rp.role_id;

    TRUNCATE TABLE role_permissions;
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT role_id, permission_id
    FROM tmp_role_permissions_new;

    IF EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_name = 'permissao_menu_perfil'
    ) THEN
      CREATE TEMP TABLE tmp_perm_menu_new ON COMMIT DROP AS
      SELECT DISTINCT
        rm.new_role_id AS role_id,
        pmp.item_menu_id
      FROM permissao_menu_perfil pmp
      JOIN tmp_role_map rm ON rm.old_role_id = pmp.role_id;

      TRUNCATE TABLE permissao_menu_perfil;
      INSERT INTO permissao_menu_perfil (role_id, item_menu_id)
      SELECT role_id, item_menu_id
      FROM tmp_perm_menu_new;
    END IF;

    IF EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_name = 'sobreposicao_perfil_menu_empresa'
    ) THEN
      CREATE TEMP TABLE tmp_sobreposicao_new ON COMMIT DROP AS
      SELECT DISTINCT
        spme.tenant_id,
        rm.new_role_id AS role_id,
        spme.item_menu_id,
        spme.enabled,
        spme.updated_by,
        spme.created_at,
        spme.updated_at
      FROM sobreposicao_perfil_menu_empresa spme
      JOIN tmp_role_map rm ON rm.old_role_id = spme.role_id;

      TRUNCATE TABLE sobreposicao_perfil_menu_empresa;
      INSERT INTO sobreposicao_perfil_menu_empresa (
        tenant_id,
        role_id,
        item_menu_id,
        enabled,
        updated_by,
        created_at,
        updated_at
      )
      SELECT
        tenant_id,
        role_id,
        item_menu_id,
        enabled,
        updated_by,
        created_at,
        updated_at
      FROM tmp_sobreposicao_new;
    END IF;

    IF EXISTS (
      SELECT 1
      FROM information_schema.tables
      WHERE table_name = 'auditoria_permissao'
    ) THEN
      UPDATE auditoria_permissao ap
      SET role_id = rm.new_role_id
      FROM tmp_role_map rm
      WHERE ap.role_id = rm.old_role_id
        AND ap.role_id <> rm.new_role_id;
    END IF;

    DELETE FROM roles r
    USING tmp_role_map rm
    WHERE r.id = rm.old_role_id
      AND rm.old_role_id <> rm.new_role_id;

    UPDATE roles
    SET name = UPPER(name);

    ALTER TABLE roles DROP CONSTRAINT IF EXISTS uq_roles_tenant_name;
    DROP INDEX IF EXISTS idx_roles_tenant_id;
    ALTER TABLE roles DROP COLUMN tenant_id;

    ALTER TABLE roles
      ADD CONSTRAINT uq_roles_name UNIQUE (name);
  END IF;
END $$;


-- <<< END V1__structure.sql

-- >>> BEGIN V3__estoque_module.sql

-- Estrutura base do modulo de estoque.

CREATE TABLE IF NOT EXISTS itens_estoque (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome VARCHAR(160) NOT NULL,
  sku VARCHAR(80),
  unidade_medida VARCHAR(20) NOT NULL,
  saldo_atual NUMERIC(19,4) NOT NULL DEFAULT 0,
  estoque_minimo NUMERIC(19,4) NOT NULL DEFAULT 0,
  custo_medio_unitario NUMERIC(19,4),
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_itens_estoque_tenant_sku
  ON itens_estoque (tenant_id, sku)
  WHERE sku IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_itens_estoque_tenant_nome
  ON itens_estoque (tenant_id, nome);

CREATE TABLE IF NOT EXISTS movimentacoes_estoque (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  item_estoque_id UUID NOT NULL REFERENCES itens_estoque(id) ON DELETE RESTRICT,
  tipo VARCHAR(20) NOT NULL,
  quantidade NUMERIC(19,4) NOT NULL,
  saldo_anterior NUMERIC(19,4) NOT NULL,
  saldo_posterior NUMERIC(19,4) NOT NULL,
  motivo VARCHAR(255) NOT NULL,
  origem VARCHAR(20) NOT NULL,
  valor_unitario_pago NUMERIC(19,4),
  valor_total_movimentacao NUMERIC(19,4),
  gerar_lancamento_financeiro BOOLEAN NOT NULL DEFAULT FALSE,
  transacao_financeira_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
  usuario_id UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_movimentacoes_estoque_tenant_item_created
  ON movimentacoes_estoque (tenant_id, item_estoque_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_movimentacoes_estoque_tenant_tipo_created
  ON movimentacoes_estoque (tenant_id, tipo, created_at DESC);

CREATE TABLE IF NOT EXISTS importacao_estoque_job (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  tipo_importacao VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  dry_run BOOLEAN NOT NULL DEFAULT FALSE,
  total_linhas INT NOT NULL DEFAULT 0,
  linhas_processadas INT NOT NULL DEFAULT 0,
  linhas_com_erro INT NOT NULL DEFAULT 0,
  arquivo_sha256 VARCHAR(128),
  arquivo_storage_key VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_importacao_estoque_job_tenant_created
  ON importacao_estoque_job (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_importacao_estoque_job_tenant_status
  ON importacao_estoque_job (tenant_id, status);

CREATE TABLE IF NOT EXISTS importacao_estoque_erro_linha (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES importacao_estoque_job(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  linha INT NOT NULL,
  coluna VARCHAR(80) NOT NULL,
  codigo_erro VARCHAR(120) NOT NULL,
  mensagem VARCHAR(400) NOT NULL,
  valor_recebido VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_importacao_estoque_erro_linha_job
  ON importacao_estoque_erro_linha (job_id, linha);

-- RBAC para modulo de estoque.
INSERT INTO permissions (id, code, description)
VALUES
  (public.uuid_generate_v4(), 'stock:view', 'Permite visualizar dados de estoque'),
  (public.uuid_generate_v4(), 'stock:manage', 'Permite gerenciar itens e movimentacoes de estoque')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT ro.id, p.id
FROM roles ro
JOIN permissions p ON p.code IN ('stock:view', 'stock:manage')
WHERE ro.name = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT ro.id, p.id
FROM roles ro
JOIN permissions p ON p.code = 'stock:view'
WHERE ro.name = 'PROFESSIONAL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Menu dinamico de estoque.
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/estoque', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/estoque', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO NOTHING;

-- <<< END V3__estoque_module.sql

-- >>> BEGIN V4__estoque_mvs.sql

-- Materialized views e log de refresh para analitico de estoque.

CREATE TABLE IF NOT EXISTS estoque_mv_refresh_log (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
  view_name VARCHAR(120) NOT NULL,
  refreshed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  duration_ms BIGINT,
  status VARCHAR(30) NOT NULL,
  error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_estoque_mv_refresh_log_view_refreshed
  ON estoque_mv_refresh_log (view_name, refreshed_at DESC);

DROP MATERIALIZED VIEW IF EXISTS mv_estoque_resumo_tenant;

CREATE MATERIALIZED VIEW mv_estoque_resumo_tenant AS
SELECT
  i.tenant_id,
  COUNT(*)::INT AS total_itens,
  COUNT(*) FILTER (WHERE i.saldo_atual <= i.estoque_minimo)::INT AS itens_abaixo_minimo,
  COUNT(*) FILTER (WHERE i.saldo_atual <= 0)::INT AS itens_zerados,
  COALESCE(SUM(i.saldo_atual * COALESCE(i.custo_medio_unitario, 0)), 0)::NUMERIC(19,4) AS valor_estoque_custo_medio,
  NOW() AS atualizado_em
FROM itens_estoque i
GROUP BY i.tenant_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_estoque_resumo_tenant
  ON mv_estoque_resumo_tenant (tenant_id);

-- <<< END V4__estoque_mvs.sql

-- >>> BEGIN V5__estoque_servico_insumo.sql

-- Fase 2: relacao entre servicos e insumos de estoque.

CREATE TABLE IF NOT EXISTS servico_insumo (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  service_id UUID NOT NULL,
  item_estoque_id UUID NOT NULL,
  quantidade_consumo NUMERIC(19,4) NOT NULL,
  percentual_perda NUMERIC(5,2) NOT NULL DEFAULT 0,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_servico_insumo_quantidade_positiva CHECK (quantidade_consumo > 0),
  CONSTRAINT chk_servico_insumo_percentual_perda_range CHECK (percentual_perda >= 0 AND percentual_perda <= 100),
  CONSTRAINT fk_servico_insumo_service FOREIGN KEY (tenant_id, service_id) REFERENCES services(tenant_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_servico_insumo_item_estoque FOREIGN KEY (item_estoque_id) REFERENCES itens_estoque(id) ON DELETE RESTRICT,
  CONSTRAINT uq_servico_insumo_tenant_servico_item UNIQUE (tenant_id, service_id, item_estoque_id)
);

CREATE INDEX IF NOT EXISTS idx_servico_insumo_tenant_servico
  ON servico_insumo (tenant_id, service_id);

CREATE INDEX IF NOT EXISTS idx_servico_insumo_tenant_item
  ON servico_insumo (tenant_id, item_estoque_id);

-- <<< END V5__estoque_servico_insumo.sql

-- >>> BEGIN V6__estoque_fase3.sql

-- Fase 3: inventarios, fornecedores, pedidos de compra, transferencias e configuracoes.

CREATE TABLE IF NOT EXISTS estoque_inventario (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome VARCHAR(200) NOT NULL,
  status VARCHAR(30) NOT NULL,
  observacao VARCHAR(500),
  data_abertura TIMESTAMPTZ NOT NULL,
  data_fechamento TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_estoque_inventario_tenant_status
  ON estoque_inventario (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS estoque_inventario_contagem (
  id UUID PRIMARY KEY,
  inventario_id UUID NOT NULL REFERENCES estoque_inventario(id) ON DELETE CASCADE,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  item_estoque_id UUID NOT NULL REFERENCES itens_estoque(id) ON DELETE RESTRICT,
  quantidade_contada NUMERIC(19,4) NOT NULL,
  observacao VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_estoque_inventario_contagem_qtd CHECK (quantidade_contada >= 0)
);

CREATE INDEX IF NOT EXISTS idx_estoque_inventario_contagem_inv
  ON estoque_inventario_contagem (inventario_id, created_at DESC);

CREATE TABLE IF NOT EXISTS estoque_fornecedor (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  nome VARCHAR(200) NOT NULL,
  documento VARCHAR(40),
  email VARCHAR(160),
  telefone VARCHAR(30),
  contato VARCHAR(160),
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_estoque_fornecedor_tenant_nome
  ON estoque_fornecedor (tenant_id, nome);

CREATE TABLE IF NOT EXISTS estoque_pedido_compra (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  fornecedor_id UUID NOT NULL REFERENCES estoque_fornecedor(id) ON DELETE RESTRICT,
  status VARCHAR(40) NOT NULL,
  valor_total NUMERIC(19,4) NOT NULL,
  quantidade_itens INT NOT NULL,
  quantidade_pendente INT NOT NULL,
  observacao VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_estoque_pedido_compra_qtd_itens CHECK (quantidade_itens > 0),
  CONSTRAINT chk_estoque_pedido_compra_qtd_pendente CHECK (quantidade_pendente >= 0),
  CONSTRAINT chk_estoque_pedido_compra_valor CHECK (valor_total >= 0)
);

CREATE INDEX IF NOT EXISTS idx_estoque_pedido_compra_tenant_status
  ON estoque_pedido_compra (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS estoque_transferencia (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  origem VARCHAR(120) NOT NULL,
  destino VARCHAR(120) NOT NULL,
  status VARCHAR(30) NOT NULL,
  item_estoque_id UUID NOT NULL REFERENCES itens_estoque(id) ON DELETE RESTRICT,
  quantidade NUMERIC(19,4) NOT NULL,
  observacao VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_estoque_transferencia_qtd CHECK (quantidade > 0)
);

CREATE INDEX IF NOT EXISTS idx_estoque_transferencia_tenant_status
  ON estoque_transferencia (tenant_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS estoque_configuracao (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
  alerta_estoque_minimo_ativo BOOLEAN NOT NULL DEFAULT TRUE,
  bloquear_saida_sem_saldo BOOLEAN NOT NULL DEFAULT TRUE,
  permitir_ajuste_negativo_com_permissao BOOLEAN NOT NULL DEFAULT FALSE,
  dias_cobertura_meta INT NOT NULL DEFAULT 15,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_estoque_configuracao_cobertura CHECK (dias_cobertura_meta > 0)
);

-- <<< END V6__estoque_fase3.sql

-- >>> BEGIN V7__lgpd_data_subject_requests.sql

CREATE TABLE lgpd_data_subject_requests (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  protocol_code VARCHAR(40) NOT NULL,
  request_type VARCHAR(40) NOT NULL,
  status VARCHAR(30) NOT NULL,
  requester_name VARCHAR(160) NOT NULL,
  requester_email VARCHAR(180) NOT NULL,
  requester_document VARCHAR(20),
  description TEXT,
  response_summary TEXT,
  assigned_to_user_id UUID REFERENCES users(id),
  created_by_user_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  closed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_lgpd_requests_tenant_protocol ON lgpd_data_subject_requests (tenant_id, protocol_code);
CREATE INDEX idx_lgpd_requests_tenant_status_created_at ON lgpd_data_subject_requests (tenant_id, status, created_at DESC, id DESC);
CREATE INDEX idx_lgpd_requests_tenant_type_created_at ON lgpd_data_subject_requests (tenant_id, request_type, created_at DESC, id DESC);
CREATE INDEX idx_lgpd_requests_requester_email ON lgpd_data_subject_requests (requester_email);

CREATE TABLE lgpd_data_subject_request_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  request_id UUID NOT NULL REFERENCES lgpd_data_subject_requests(id),
  event_type VARCHAR(40) NOT NULL,
  previous_status VARCHAR(30),
  new_status VARCHAR(30),
  event_note TEXT,
  actor_user_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lgpd_request_events_request_created_at ON lgpd_data_subject_request_events (request_id, created_at DESC, id DESC);
CREATE INDEX idx_lgpd_request_events_tenant_created_at ON lgpd_data_subject_request_events (tenant_id, created_at DESC, id DESC);

-- <<< END V7__lgpd_data_subject_requests.sql

-- >>> BEGIN V8__mfa_totp_users.sql

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS mfa_secret_enc TEXT;


-- <<< END V8__mfa_totp_users.sql

-- >>> BEGIN V9__fiscal_sequence_control.sql

CREATE TABLE IF NOT EXISTS fiscal_sequence_control (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  modelo VARCHAR(10) NOT NULL,
  serie INT NOT NULL,
  ambiente VARCHAR(20) NOT NULL,
  ultimo_numero INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_fiscal_sequence_control UNIQUE (tenant_id, modelo, serie, ambiente)
);

CREATE INDEX IF NOT EXISTS idx_fiscal_sequence_control_tenant
  ON fiscal_sequence_control (tenant_id);

-- <<< END V9__fiscal_sequence_control.sql

-- >>> BEGIN V10__fiscal_events_and_danfe_jobs.sql

CREATE TABLE IF NOT EXISTS fiscal_invoice_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id UUID NOT NULL REFERENCES fiscal_invoices(id) ON DELETE CASCADE,
  event_type VARCHAR(40) NOT NULL,
  event_status VARCHAR(20) NOT NULL,
  sefaz_status_code VARCHAR(20),
  sefaz_status_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fiscal_invoice_events_tenant_invoice_created
  ON fiscal_invoice_events (tenant_id, invoice_id, created_at DESC);

CREATE TABLE IF NOT EXISTS fiscal_danfe_jobs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id UUID NOT NULL REFERENCES fiscal_invoices(id) ON DELETE CASCADE,
  requested_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  status VARCHAR(20) NOT NULL,
  pdf_storage_key VARCHAR(255),
  error_code VARCHAR(80),
  error_message TEXT,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_fiscal_danfe_jobs_tenant_invoice
  ON fiscal_danfe_jobs (tenant_id, invoice_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_fiscal_danfe_jobs_tenant_status
  ON fiscal_danfe_jobs (tenant_id, status, requested_at ASC);

-- <<< END V10__fiscal_events_and_danfe_jobs.sql

-- >>> BEGIN V11__fiscal_certificates.sql

CREATE TABLE IF NOT EXISTS fiscal_certificates (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  certificate_pfx_enc TEXT NOT NULL,
  thumbprint VARCHAR(128) NOT NULL,
  subject_name VARCHAR(255) NOT NULL,
  valid_to TIMESTAMPTZ NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_certificates_active_per_tenant
  ON fiscal_certificates (tenant_id)
  WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_fiscal_certificates_tenant_status
  ON fiscal_certificates (tenant_id, status);

-- <<< END V11__fiscal_certificates.sql

-- >>> BEGIN V12__fiscal_invoices_modelo_serie_numero_ambiente.sql

ALTER TABLE fiscal_invoices
  ADD COLUMN IF NOT EXISTS modelo VARCHAR(10),
  ADD COLUMN IF NOT EXISTS serie INT,
  ADD COLUMN IF NOT EXISTS numero INT,
  ADD COLUMN IF NOT EXISTS ambiente VARCHAR(20);

UPDATE fiscal_invoices
SET modelo = CASE
    WHEN upper(coalesce(invoice_type, '')) IN ('NFCE', '65') THEN '65'
    ELSE '55'
  END
WHERE modelo IS NULL;

UPDATE fiscal_invoices
SET serie = CASE
    WHEN serie_nf ~ '^[0-9]+$' THEN CAST(serie_nf AS INT)
    ELSE 1
  END
WHERE serie IS NULL;

UPDATE fiscal_invoices
SET numero = CASE
    WHEN numero_nf ~ '^[0-9]+$' THEN CAST(numero_nf AS INT)
    ELSE NULL
  END
WHERE numero IS NULL;

UPDATE fiscal_invoices
SET ambiente = 'HOMOLOGACAO'
WHERE ambiente IS NULL OR trim(ambiente) = '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_invoices_tenant_modelo_serie_numero_ambiente
  ON fiscal_invoices (tenant_id, modelo, serie, numero, ambiente)
  WHERE numero IS NOT NULL;

-- <<< END V12__fiscal_invoices_modelo_serie_numero_ambiente.sql

-- >>> BEGIN V13__fiscal_idempotency_requests.sql

CREATE TABLE IF NOT EXISTS fiscal_idempotency_requests (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  operation VARCHAR(80) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  response_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_idempotency_request
  ON fiscal_idempotency_requests (tenant_id, operation, idempotency_key);

-- <<< END V13__fiscal_idempotency_requests.sql

-- >>> BEGIN V14__fiscal_xml_signed_encrypted.sql

ALTER TABLE fiscal_invoices
  ADD COLUMN IF NOT EXISTS xml_signed_enc TEXT;

-- <<< END V14__fiscal_xml_signed_encrypted.sql

-- >>> BEGIN V15__fiscal_code_catalog.sql

CREATE TABLE IF NOT EXISTS fiscal_code_catalog (
  id UUID PRIMARY KEY,
  code_type VARCHAR(20) NOT NULL,
  code_value VARCHAR(20) NOT NULL,
  description VARCHAR(255),
  valid_from DATE NOT NULL,
  valid_to DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_fiscal_code_catalog_type_value_valid_from
  ON fiscal_code_catalog (code_type, code_value, valid_from);

CREATE INDEX IF NOT EXISTS idx_fiscal_code_catalog_type_status
  ON fiscal_code_catalog (code_type, status);

-- <<< END V15__fiscal_code_catalog.sql

-- >>> BEGIN V16__fiscal_danfe_download_policy.sql

ALTER TABLE fiscal_danfe_jobs
  ADD COLUMN IF NOT EXISTS download_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE fiscal_danfe_jobs
  ADD COLUMN IF NOT EXISTS downloaded_at TIMESTAMPTZ;

ALTER TABLE fiscal_danfe_jobs
  ADD COLUMN IF NOT EXISTS download_expires_at TIMESTAMPTZ;

-- <<< END V16__fiscal_danfe_download_policy.sql

-- >>> BEGIN V17__fiscal_tax_config_issuer_fields.sql

ALTER TABLE fiscal_tax_configs
  ADD COLUMN IF NOT EXISTS issuer_razao_social VARCHAR(160),
  ADD COLUMN IF NOT EXISTS issuer_nome_fantasia VARCHAR(160),
  ADD COLUMN IF NOT EXISTS issuer_cnpj VARCHAR(20),
  ADD COLUMN IF NOT EXISTS issuer_ie VARCHAR(30),
  ADD COLUMN IF NOT EXISTS issuer_im VARCHAR(30),
  ADD COLUMN IF NOT EXISTS issuer_phone VARCHAR(30),
  ADD COLUMN IF NOT EXISTS issuer_email VARCHAR(120),
  ADD COLUMN IF NOT EXISTS issuer_street VARCHAR(160),
  ADD COLUMN IF NOT EXISTS issuer_number VARCHAR(20),
  ADD COLUMN IF NOT EXISTS issuer_complement VARCHAR(120),
  ADD COLUMN IF NOT EXISTS issuer_neighborhood VARCHAR(120),
  ADD COLUMN IF NOT EXISTS issuer_city VARCHAR(120),
  ADD COLUMN IF NOT EXISTS issuer_state VARCHAR(2),
  ADD COLUMN IF NOT EXISTS issuer_zip_code VARCHAR(12),
  ADD COLUMN IF NOT EXISTS issuer_uf_code VARCHAR(2),
  ADD COLUMN IF NOT EXISTS nfce_csc_homologation VARCHAR(120),
  ADD COLUMN IF NOT EXISTS nfce_csc_id_token_homologation VARCHAR(20),
  ADD COLUMN IF NOT EXISTS nfce_csc_production VARCHAR(120),
  ADD COLUMN IF NOT EXISTS nfce_csc_id_token_production VARCHAR(20);

-- <<< END V17__fiscal_tax_config_issuer_fields.sql

-- >>> BEGIN V18__fiscal_code_catalog_nfse_nbs.sql

-- Fonte oficial: https://www.gov.br/nfse/pt-br/mei-e-demais-empresas/codigos-de-tributacao-nacional-nbs
-- Gerado em: 2026-03-03

ALTER TABLE fiscal_code_catalog
  ALTER COLUMN description TYPE TEXT;

INSERT INTO fiscal_code_catalog (id, code_type, code_value, description, valid_from, status, created_at)
VALUES
  ('7b45ab5d-c961-4c08-b879-ed5357b1a79f', 'NBS', '010101', 'Análise e desenvolvimento de sistemas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('965b74b9-145e-49b3-88d0-3e2d405fe7a4', 'NBS', '010201', 'Programação.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('68e6166c-603d-4ead-a0ea-f0f2afc45b14', 'NBS', '010301', 'Processamento de dados, textos, imagens, vídeos, páginas eletrônicas, aplicativos e sistemas de informação, entre outros formatos, e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('013734fe-680d-4d0b-9b3e-b6607f2e2768', 'NBS', '010302', 'Armazenamento ou hospedagem de dados, textos, imagens, vídeos, páginas eletrônicas, aplicativos e sistemas de informação, entre outros formatos, e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c0fb2574-4ef0-470e-aef5-1a9977901d76', 'NBS', '010401', 'Elaboração de programas de computadores, inclusive de jogos eletrônicos, independentemente da arquitetura construtiva da máquina em que o programa será executado, incluindo tablets, smartphones e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3aef343d-1ac9-44c1-a955-b3782e3b71e6', 'NBS', '010501', 'Licenciamento ou cessão de direito de uso de programas de computação.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5591a374-ea3d-46ee-8e72-d86ade26bb18', 'NBS', '010601', 'Assessoria e consultoria em informática.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c1dd9573-399d-4dee-9f24-af2f3313ee5c', 'NBS', '010701', 'Suporte técnico em informática, inclusive instalação, configuração e manutenção de programas de computação e bancos de dados.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cf27be50-af94-4b7b-b7a2-0e8dc3b63688', 'NBS', '010801', 'Planejamento, confecção, manutenção e atualização de páginas eletrônicas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d1414a0f-fca2-49d8-a334-9d718cd2e019', 'NBS', '010901', 'Disponibilização, sem cessão definitiva, de conteúdos de áudio por meio da internet (exceto a distribuição de conteúdos pelas prestadoras de Serviço de Acesso Condicionado, de que trata a Lei nº 12.485, de 12 de setembro de 2011, sujeita ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cef12198-1c2e-4262-b5eb-4f4a59c26b4c', 'NBS', '010902', 'Disponibilização, sem cessão definitiva, de conteúdos de vídeo, imagem e texto por meio da internet, respeitada a imunidade de livros, jornais e periódicos (exceto a distribuição de conteúdos pelas prestadoras de Serviço de Acesso Condicionado, de que trata a Lei nº 12.485, de 12 de setembro de 2011, sujeita ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('06205b06-9d97-444b-bdd4-21301f070781', 'NBS', '020101', 'Serviços de pesquisas e desenvolvimento de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6cfd622e-c4ed-4174-8b72-258d494d2af7', 'NBS', '030201', 'Cessão de direito de uso de marcas e de sinais de propaganda.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a49fe8b6-eae1-42da-8765-226e94e84d68', 'NBS', '030301', 'Exploração de salões de festas, centro de convenções, stands e congêneres, para realização de eventos ou negócios de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('32fa63c3-560d-44f2-97e4-4eb36a69c14c', 'NBS', '030302', 'Exploração de escritórios virtuais e congêneres, para realização de eventos ou negócios de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b142dc52-8ce8-4e39-be8a-630deab10bc0', 'NBS', '030303', 'Exploração de quadras esportivas, estádios, ginásios, canchas e congêneres, para realização de eventos ou negócios de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f676e2ad-6a93-4f53-bcb9-703b1ef9d527', 'NBS', '030304', 'Exploração de auditórios, casas de espetáculos e congêneres, para realização de eventos ou negócios de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3330f790-a9d0-465a-8f4e-5754482f916b', 'NBS', '030305', 'Exploração de parques de diversões e congêneres, para realização de eventos ou negócios de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3805621c-5e05-48c6-96f6-52563d93cf1a', 'NBS', '030401', 'Locação, sublocação, arrendamento, direito de passagem ou permissão de uso, compartilhado ou não, de ferrovia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('58b95767-d914-4643-903b-c707cf1978da', 'NBS', '030402', 'Locação, sublocação, arrendamento, direito de passagem ou permissão de uso, compartilhado ou não, de rodovia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('dba2745c-48f6-4976-88d3-9d657f3aaa14', 'NBS', '030403', 'Locação, sublocação, arrendamento, direito de passagem ou permissão de uso, compartilhado ou não, de postes, cabos, dutos e condutos de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6df281ff-f64a-403c-9eaa-915c9500f913', 'NBS', '030501', 'Cessão de andaimes, palcos, coberturas e outras estruturas de uso temporário.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9bcea525-2dfd-4b59-afb7-4b4fc7c09b44', 'NBS', '040101', 'Medicina.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8a4619fd-dae8-44c8-bb5f-7490dad6a8d7', 'NBS', '040102', 'Biomedicina.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('50c0d323-2923-4655-a155-e75204a3a6a9', 'NBS', '040201', 'Análises clínicas e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('65e3b397-06d0-44e1-9f22-988be0ebf9c7', 'NBS', '040202', 'Patologia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('64a521b2-15b1-40b4-b92d-15b06fa5003c', 'NBS', '040203', 'Eletricidade médica (eletroestimulação de nervos e musculos, cardioversão, etc) e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('91c7e47b-bd4e-4a54-8425-38007a1b01e5', 'NBS', '040204', 'Radioterapia, quimioterapia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('500f4433-7eeb-46b4-a6c0-70eb57975e4c', 'NBS', '040205', 'Ultra-sonografia, ressonância magnética, radiologia, tomografia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cb92d3ab-13c8-49d1-9f74-07151a6fb4d4', 'NBS', '040301', 'Hospitais e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f32866a0-e57e-4646-849b-de9951c640b4', 'NBS', '040302', 'Laboratórios e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('68446be4-01e7-4cbd-b274-4879a660d56f', 'NBS', '040303', 'Clínicas, sanatórios, manicômios, casas de saúde, prontos-socorros, ambulatórios e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('31e7603a-06b9-4a93-8463-44994a4028e4', 'NBS', '040401', 'Instrumentação cirúrgica.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2e886cca-fa63-4252-8822-cf206453a787', 'NBS', '040501', 'Acupuntura.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1d0f283b-b5cb-44f7-a123-f211dc78e5e2', 'NBS', '040601', 'Enfermagem, inclusive serviços auxiliares.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('45d5fc3b-3a80-4198-8d1d-28b7a130ba80', 'NBS', '040701', 'Serviços farmacêuticos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c75801eb-1d85-461a-ad11-9cfb0b912caf', 'NBS', '040801', 'Terapia ocupacional.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('aa8b837d-c91a-4b73-a3d3-47dedb93e140', 'NBS', '040802', 'Fisioterapia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('831f4642-6c0f-4961-987f-29dd23017b5a', 'NBS', '040803', 'Fonoaudiologia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ecb464e9-3bec-473d-a5e7-caa4ff436132', 'NBS', '040901', 'Terapias de qualquer espécie destinadas ao tratamento físico, orgânico e mental.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('db02cde6-991c-4ef0-a36a-be899cf4aba5', 'NBS', '041001', 'Nutrição.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a342e729-7164-49bb-842c-63630f1f099d', 'NBS', '041101', 'Obstetrícia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0ad109aa-fc8d-41c6-983f-5eb9605d3b82', 'NBS', '041201', 'Odontologia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('eda61133-7bc7-486b-ace0-5ce1e890f890', 'NBS', '041301', 'Ortóptica.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('74174251-2d73-4332-8a98-2e3599da2ba3', 'NBS', '041401', 'Próteses sob encomenda.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('18db573d-34a8-4969-9779-cbaf1ff9e399', 'NBS', '041501', 'Psicanálise.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2a3891c6-9f77-4a19-a551-e67506f7c5d9', 'NBS', '041601', 'Psicologia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('291dba51-817b-459e-acb7-7352148d570c', 'NBS', '041701', 'Casas de repouso e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('35ac0f2f-963d-4372-a95b-bf45a6e8a498', 'NBS', '041702', 'Casas de recuperação e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3d08c03a-8378-4166-9ee2-b53a29118aa6', 'NBS', '041703', 'Creches e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7a0ad9d6-a6b7-412c-8da8-14559ea9b64f', 'NBS', '041704', 'Asilos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c1ea308b-266d-40ec-ad29-2acf45fd28b3', 'NBS', '041801', 'Inseminação artificial, fertilização in vitro e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('63341313-0e56-4366-b886-810c58b2be0a', 'NBS', '041901', 'Bancos de sangue, leite, pele, olhos, óvulos, sêmen e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('edf0e771-fb5e-4da6-bfcc-f369a37f5824', 'NBS', '042001', 'Coleta de sangue, leite, tecidos, sêmen, órgãos e materiais biológicos de qualquer espécie.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('42418801-d5be-47e3-b876-8a50f647b3ac', 'NBS', '042101', 'Unidade de atendimento, assistência ou tratamento móvel e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7899a157-dd34-4830-9f6a-ec61b83b022d', 'NBS', '042201', 'Planos de medicina de grupo ou individual e convênios para prestação de assistência médica, hospitalar, odontológica e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('21b447ba-0a28-4e2c-ad91-42ad4e1222c1', 'NBS', '042301', 'Outros planos de saúde que se cumpram através de serviços de terceiros contratados, credenciados, cooperados ou apenas pagos pelo operador do plano mediante indicação do beneficiário.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('509af3d7-03b8-4e22-9929-f8a58a95d33a', 'NBS', '050101', 'Medicina veterinária', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('58959182-809f-42ef-8349-b81e54c14db7', 'NBS', '050102', 'Zootecnia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0cad4fc4-548a-434a-b50d-e9dd737d4bd9', 'NBS', '050201', 'Hospitais e congêneres, na área veterinária.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c6a7933b-4c40-440b-bf48-8fbc0fe0ceb6', 'NBS', '050202', 'Clínicas, ambulatórios, prontos-socorros e congêneres, na área veterinária.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c2ef0d72-035d-48f3-bba5-d39a1f9d3eb0', 'NBS', '050301', 'Laboratórios de análise na área veterinária.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ffee7846-2b83-4d04-8fab-c590c171c308', 'NBS', '050401', 'Inseminação artificial, fertilização in vitro e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('94bd2786-402e-4aa9-a2e9-7bd7cac60a70', 'NBS', '050501', 'Bancos de sangue e de órgãos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d3438b37-aaa2-4e31-9929-134543d8d52f', 'NBS', '050601', 'Coleta de sangue, leite, tecidos, sêmen, órgãos e materiais biológicos de qualquer espécie.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5dd090b9-16f1-4685-aec5-4fb134e2f7e4', 'NBS', '050701', 'Unidade de atendimento, assistência ou tratamento móvel e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2606d98f-1bae-454c-a699-666ed4b48912', 'NBS', '050801', 'Guarda, tratamento, amestramento, embelezamento, alojamento e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('fdf9a052-3280-4678-a217-94cbf71179c8', 'NBS', '050901', 'Planos de atendimento e assistência médico-veterinária.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c1d992a9-1a22-4241-8fb5-13761d06586d', 'NBS', '060101', 'Barbearia, cabeleireiros, manicuros, pedicuros e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3988cc1d-6a53-40f9-a11b-eb6c496cc7e9', 'NBS', '060201', 'Esteticistas, tratamento de pele, depilação e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('aa8ad795-11be-4a78-9664-cb88f730d150', 'NBS', '060301', 'Banhos, duchas, sauna, massagens e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('69a20536-c1ca-40ef-af1e-413bbd84a6ec', 'NBS', '060401', 'Ginástica, dança, esportes, natação, artes marciais e demais atividades físicas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('bca94a43-7f9a-40b4-9b85-e1dee7ca4c07', 'NBS', '060501', 'Centros de emagrecimento, spa e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('314f34df-009d-43bf-91ad-3da5ccda4363', 'NBS', '060601', 'Aplicação de tatuagens, piercings e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('07604109-02a3-4a47-b026-f5493bf4b038', 'NBS', '070101', 'Engenharia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('43ff8839-6531-4ba5-a887-66c86d5b17fd', 'NBS', '070102', 'Agronomia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8be3e8a5-fed2-43d4-8537-e03b0c04201f', 'NBS', '070103', 'Agrimensura e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2c2ce14a-ad84-4315-af7e-2b5e656ef20b', 'NBS', '070104', 'Arquitetura, urbanismo e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6b4fee4c-6120-4d23-89d8-cf5b7b913888', 'NBS', '070105', 'Geologia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9c2b897e-7afb-4883-ab55-d9725fca5c4b', 'NBS', '070106', 'Paisagismo e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9f39459c-aaee-4346-b14e-9f00ee7aee4f', 'NBS', '070201', 'Execução, por administração, de obras de construção civil, hidráulica ou elétrica e de outras obras semelhantes, inclusive sondagem, perfuração de poços, escavação, drenagem e irrigação, terraplanagem, pavimentação, concretagem e a instalação e montagem de produtos, peças e equipamentos (exceto o fornecimento de mercadorias produzidas pelo prestador de serviços fora do local da prestação dos serviços, que fica sujeito ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('bbcbd7b1-6fa3-475b-8226-1dd9e881f55f', 'NBS', '070202', 'Execução, por empreitada ou subempreitada, de obras de construção civil, hidráulica ou elétrica e de outras obras semelhantes, inclusive sondagem, perfuração de poços, escavação, drenagem e irrigação, terraplanagem, pavimentação, concretagem e a instalação e montagem de produtos, peças e equipamentos (exceto o fornecimento de mercadorias produzidas pelo prestador de serviços fora do local da prestação dos serviços, que fica sujeito ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8bfdc089-3eeb-4068-9be2-50bdd84efccf', 'NBS', '070301', 'Elaboração de planos diretores, estudos de viabilidade, estudos organizacionais e outros, relacionados com obras e serviços de engenharia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('41fdcfee-8c77-4835-975b-27041438f578', 'NBS', '070302', 'Elaboração de anteprojetos, projetos básicos e projetos executivos para trabalhos de engenharia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b1074151-fc73-4181-b4fb-aebd2817d9e3', 'NBS', '070401', 'Demolição.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('11453b44-f4d4-4556-8738-1cc80d735e59', 'NBS', '070501', 'Reparação, conservação e reforma de edifícios e congêneres (exceto o fornecimento de mercadorias produzidas pelo prestador dos serviços, fora do local da prestação dos serviços, que fica sujeito ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('59d6c5f9-c24b-4ecf-8b2f-11e609ed9c36', 'NBS', '070502', 'Reparação, conservação e reforma de estradas, pontes, portos e congêneres (exceto o fornecimento de mercadorias produzidas pelo prestador dos serviços, fora do local da prestação dos serviços, que fica sujeito ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d1a48f48-ade9-43f2-8e0d-0594417090e7', 'NBS', '070601', 'Colocação e instalação de tapetes, carpetes, cortinas e congêneres, com material fornecido pelo tomador do serviço.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0234e8f6-7871-4cda-bbe6-eb41145a65e4', 'NBS', '070602', 'Colocação e instalação de assoalhos, revestimentos de parede, vidros, divisórias, placas de gesso e congêneres, com material fornecido pelo tomador do serviço.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('da1c2f98-fca4-424d-b18f-9cf6e6e430ea', 'NBS', '070701', 'Recuperação, raspagem, polimento e lustração de pisos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('30a74705-f9c7-4621-8110-36fb487e6ab2', 'NBS', '070801', 'Calafetação.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('fbcd7fd1-7c05-4297-9584-757b68445c5c', 'NBS', '070901', 'Varrição, coleta e remoção de lixo, rejeitos e outros resíduos quaisquer.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b048c8d9-84d8-4cd1-98e6-d5367913d922', 'NBS', '070902', 'Incineração, tratamento, reciclagem, separação e destinação final de lixo, rejeitos e outros resíduos quaisquer.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6fd9e83b-6cdf-4a81-8273-d5d6088e185f', 'NBS', '071001', 'Limpeza, manutenção e conservação de vias e logradouros públicos, parques, jardins e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('df53e7b9-ade3-43cd-8229-96a2dc0e5acb', 'NBS', '071002', 'Limpeza, manutenção e conservação de imóveis, chaminés, piscinas e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('923a36d0-67ef-4f55-b2e6-be397bd2f3bc', 'NBS', '071101', 'Decoração.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('121a49be-40a7-41b7-9f92-4eeff781b4cd', 'NBS', '071102', 'Jardinagem, inclusive corte e poda de árvores.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a9a8ab62-2855-42c2-98b0-06e5563b6911', 'NBS', '071201', 'Controle e tratamento de efluentes de qualquer natureza e de agentes físicos, químicos e biológicos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('535560af-f270-4a70-905b-df66eacdd909', 'NBS', '071301', 'Dedetização, desinfecção, desinsetização, imunização, higienização, desratização, pulverização e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ef4a9526-4e90-4895-8796-61022e98a820', 'NBS', '071601', 'Florestamento, reflorestamento, semeadura, adubação, reparação de solo, plantio, silagem, colheita, corte e descascamento de árvores, silvicultura, exploração florestal e dos serviços congêneres indissociáveis da formação, manutenção e colheita de florestas, para quaisquer fins e por quaisquer meios.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('452bbc07-06a1-4d51-b70c-70f9e03dfa5f', 'NBS', '071701', 'Escoramento, contenção de encostas e serviços congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('29aca915-24db-4247-adcc-a25760047801', 'NBS', '071801', 'Limpeza e dragagem de rios, portos, canais, baías, lagos, lagoas, represas, açudes e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('bd72dde2-5901-4b38-b504-a23fc005a8fe', 'NBS', '071901', 'Acompanhamento e fiscalização da execução de obras de engenharia, arquitetura e urbanismo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('88bbe40a-6cf6-4217-b873-e482090bacc7', 'NBS', '072001', 'Aerofotogrametria (inclusive interpretação), cartografia, mapeamento e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('484b33e6-59b3-41ce-8c31-4ffd4b8c8de9', 'NBS', '072002', 'Levantamentos batimétricos, geográficos, geodésicos, geológicos, geofísicos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('bb92e01a-dae9-4285-affe-b265eef54ad4', 'NBS', '072003', 'Levantamentos topográficos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('94b7442a-7c99-4f24-8f11-3f5bcc6ab35a', 'NBS', '072101', 'Pesquisa, perfuração, cimentação, mergulho, perfilagem, concretação, testemunhagem, pescaria, estimulação e outros serviços relacionados com a exploração e explotação de petróleo, gás natural e de outros recursos minerais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('01a77950-2dc8-4afc-aad9-60a44dbfd139', 'NBS', '072201', 'Nucleação e bombardeamento de nuvens e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ce3c1986-fdd9-4c1e-854b-ac0131a205b2', 'NBS', '080101', 'Ensino regular pré-escolar, fundamental e médio.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1ca40742-5bb7-4630-8f72-10e95c2414e9', 'NBS', '080102', 'Ensino regular superior.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0f73b163-32f5-4c49-b2fe-afa7043bf2a6', 'NBS', '080201', 'Instrução, treinamento, orientação pedagógica e educacional, avaliação de conhecimentos de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5f8481ef-890d-44b1-a6be-f20947f806ac', 'NBS', '090101', 'Hospedagem em hotéis, hotelaria marítima e congêneres (o valor da alimentação e gorjeta, quando incluído no preço da diária, fica sujeito ao Imposto Sobre Serviços).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('20fea403-7078-430e-ad0b-25fe1a40b261', 'NBS', '090102', 'Hospedagem em pensões, albergues, pousadas, hospedarias, ocupação por temporada com fornecimento de serviços e congêneres (o valor da alimentação e gorjeta, quando incluído no preço da diária, fica sujeito ao Imposto Sobre Serviços).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8ccb3fab-3de9-487c-b540-198aa413c0cd', 'NBS', '090103', 'Hospedagem em motéis e congêneres (o valor da alimentação e gorjeta, quando incluído no preço da diária, fica sujeito ao Imposto Sobre Serviços).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0eb13d35-6fa5-472f-9ade-9e2ca611aba6', 'NBS', '090104', 'Hospedagem em apart-service condominiais, flat, apart-hotéis, hotéis residência, residence-service, suite service e congêneres (o valor da alimentação e gorjeta, quando incluído no preço da diária, fica sujeito ao Imposto Sobre Serviços).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b55f3033-79a0-49ce-87a4-7abb18677e6f', 'NBS', '090201', 'Agenciamento e intermediação de programas de turismo, passeios, viagens, excursões, hospedagens e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6424b9d1-be4a-4d7a-b913-9066ab766e43', 'NBS', '090202', 'Organização, promoção e execução de programas de turismo, passeios, viagens, excursões, hospedagens e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('38151b4b-b676-4928-b9ac-31fbba968b26', 'NBS', '090301', 'Guias de turismo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('962ee541-024b-46ef-9c37-b9b717cbcd4e', 'NBS', '100101', 'Agenciamento, corretagem ou intermediação de câmbio.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8e2764f6-a797-43c0-a6ef-be3f75cb6814', 'NBS', '100102', 'Agenciamento, corretagem ou intermediação de seguros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('982a0e44-2bd6-4ff7-a109-92db5ac37806', 'NBS', '100103', 'Agenciamento, corretagem ou intermediação de cartões de crédito.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c3da2a4f-f596-441c-aa7f-2ea7efed0be5', 'NBS', '100104', 'Agenciamento, corretagem ou intermediação de planos de saúde.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('147e46d9-52f9-4ae6-8411-7a6291e8e296', 'NBS', '100105', 'Agenciamento, corretagem ou intermediação de planos de previdência privada.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c0eab0fa-b9ce-4994-9373-65ca5989aa06', 'NBS', '100201', 'Agenciamento, corretagem ou intermediação de títulos em geral e valores mobiliários.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('28a747c8-4f44-4b07-bcac-541faab86a07', 'NBS', '100202', 'Agenciamento, corretagem ou intermediação de contratos quaisquer.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('878b7538-e0d0-4642-a720-a756526dbdd6', 'NBS', '100301', 'Agenciamento, corretagem ou intermediação de direitos de propriedade industrial, artística ou literária.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('fd93fd27-2ac8-4268-af4b-3a686100d9d2', 'NBS', '100401', 'Agenciamento, corretagem ou intermediação de contratos de arrendamento mercantil (leasing).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8a8ce765-d456-494d-835a-b0200fbfd03a', 'NBS', '100402', 'Agenciamento, corretagem ou intermediação de contratos de franquia (franchising).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8e0740f6-8874-4a80-b4f6-b6081d922ec5', 'NBS', '100403', 'Agenciamento, corretagem ou intermediação de faturização (factoring).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('36a5a207-3e7f-415c-97d0-c4cdb85a134d', 'NBS', '100501', 'Agenciamento, corretagem ou intermediação de bens móveis ou imóveis, não abrangidos em outros itens ou subitens, por quaisquer meios.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c8e0f82c-a4b6-40e2-aa71-9a388a00ba81', 'NBS', '100502', 'Agenciamento, corretagem ou intermediação de bens móveis ou imóveis realizados no âmbito de Bolsas de Mercadorias e Futuros, por quaisquer meios.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cb819cc3-00a0-4c42-9da0-cf4c90440a91', 'NBS', '100601', 'Agenciamento marítimo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('76305752-4e10-4e27-a815-5b3ba3126e8d', 'NBS', '100701', 'Agenciamento de notícias.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7d4963b0-c0c0-4e7f-9334-98aabec535e1', 'NBS', '100801', 'Agenciamento de publicidade e propaganda, inclusive o agenciamento de veiculação por quaisquer meios.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('36a633a8-e51a-4e3c-a3ca-890ccc630871', 'NBS', '100901', 'Representação de qualquer natureza, inclusive comercial.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('977dcc98-d511-4b48-8aed-dd602dac107c', 'NBS', '101001', 'Distribuição de bens de terceiros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b30aa4e8-0774-45c6-ab74-9a856c0f37b7', 'NBS', '110101', 'Guarda e estacionamento de veículos terrestres automotores.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('833b942b-ff5e-4253-9a3a-cfde0aaa7f49', 'NBS', '110102', 'Guarda e estacionamento de aeronaves e de embarcações.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7f6079dd-5f28-4ecc-a90b-7742b4df5ac6', 'NBS', '110201', 'Vigilância, segurança ou monitoramento de bens, pessoas e semoventes.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('4685406f-603e-4886-b710-f89202a8dc85', 'NBS', '110301', 'Escolta, inclusive de veículos e cargas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('57c0f892-ea0f-4839-b287-56159085a49b', 'NBS', '110401', 'Armazenamento, depósito, guarda de bens de qualquer espécie.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cad970d9-7b3d-49b5-ba34-327d102a3473', 'NBS', '110402', 'Carga, descarga, arrumação de bens de qualquer espécie.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('574cf462-b5b8-4143-9209-001bcd7bab7f', 'NBS', '120101', 'Espetáculos teatrais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3ca4d4ae-6f39-40dd-a747-437d4403f0ad', 'NBS', '120201', 'Exibições cinematográficas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('fb9add82-576b-4d59-92b3-2cc9a6943722', 'NBS', '120301', 'Espetáculos circenses.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cbd09b9b-f26d-4749-8699-8cb79b631691', 'NBS', '120401', 'Programas de auditório.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c806024a-4cf8-4f8c-8865-c8610d38bc2f', 'NBS', '120501', 'Parques de diversões, centros de lazer e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('16f7583e-5224-45ca-b27a-e648c4116198', 'NBS', '120601', 'Boates, taxi-dancing e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1da07db6-7d83-4820-9a9d-56a6fea75ac6', 'NBS', '120701', 'Shows, ballet, danças, desfiles, bailes, óperas, concertos, recitais, festivais e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f5e3325f-00d3-4496-b049-4bd35d4301f8', 'NBS', '120801', 'Feiras, exposições, congressos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f1ced811-cc11-462a-8d14-a695e3c80ce6', 'NBS', '120901', 'Bilhares.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('29b96f1d-4aa6-46c2-9c9b-65c0957ca46f', 'NBS', '120902', 'Boliches.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a6889730-43c2-403d-963b-731d6d0cc5e1', 'NBS', '120903', 'Diversões eletrônicas ou não.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('235138a3-e092-4d58-b1d0-364d1c166427', 'NBS', '121001', 'Corridas e competições de animais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d2e846f4-9eb7-46e5-a962-8e4895687c90', 'NBS', '121101', 'Competições esportivas ou de destreza física ou intelectual, com ou sem a participação do espectador.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('35686744-ccb5-4a8a-a5e1-9e35a9946990', 'NBS', '121201', 'Execução de música.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('4bc9d63a-f2f7-4e5a-90ac-8b7981cdd6e6', 'NBS', '121301', 'Produção, mediante ou sem encomenda prévia, de eventos, espetáculos, entrevistas, shows, ballet, danças, desfiles, bailes, teatros, óperas, concertos, recitais, festivais e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3b34cbde-b822-4acb-9976-9ccddda1efb4', 'NBS', '121401', 'Fornecimento de música para ambientes fechados ou não, mediante transmissão por qualquer processo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('12691958-96dc-42a0-a6d7-516bab5574ce', 'NBS', '121501', 'Desfiles de blocos carnavalescos ou folclóricos, trios elétricos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3cf7d9f6-2dd9-45fb-85ae-56384a464497', 'NBS', '121601', 'Exibição de filmes, entrevistas, musicais, espetáculos, shows, concertos, desfiles, óperas, competições esportivas, de destreza intelectual ou congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('418bac6e-9c3f-4370-bf0f-8cbef6bcd6ad', 'NBS', '121701', 'Recreação e animação, inclusive em festas e eventos de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8d792529-c295-4065-a8d9-9958c6085fb2', 'NBS', '130201', 'Fonografia ou gravação de sons, inclusive trucagem, dublagem, mixagem e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6a567627-316b-405a-84b0-ede535540f54', 'NBS', '130301', 'Fotografia e cinematografia, inclusive revelação, ampliação, cópia, reprodução, trucagem e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a44814ac-854a-466f-bfd7-e5939b0feef8', 'NBS', '130401', 'Reprografia, microfilmagem e digitalização.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5b98a24e-97f1-45f7-a869-16ac2bac5898', 'NBS', '130501', 'Composição gráfica, inclusive confecção de impressos gráficos, fotocomposição, clicheria, zincografia, litografia e fotolitografia, exceto se destinados a posterior operação de comercialização ou industrialização, ainda que incorporados, de qualquer forma, a outra mercadoria que deva ser objeto de posterior circulação, tais como bulas, rótulos, etiquetas, caixas, cartuchos, embalagens e manuais técnicos e de instrução, quando ficarão sujeitos ao ICMS.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('97302f9a-6ac1-4ca8-958a-620b10de51b6', 'NBS', '140101', 'Lubrificação, limpeza, lustração, revisão, carga e recarga, conserto, restauração, blindagem, manutenção e conservação de máquinas, veículos, aparelhos, equipamentos, motores, elevadores ou de qualquer objeto (exceto peças e partes empregadas, que ficam sujeitas ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5f3be143-e2e5-43e2-a486-36a928dcb1d3', 'NBS', '140201', 'Assistência técnica.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1704238f-4087-42c8-abfc-839e1c1518cd', 'NBS', '140301', 'Recondicionamento de motores (exceto peças e partes empregadas, que ficam sujeitas ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('64484027-dd39-49af-891e-5bfe42893209', 'NBS', '140401', 'Recauchutagem ou regeneração de pneus.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a90a0533-de35-4488-b156-93ffcfa2f9d9', 'NBS', '140501', 'Restauração, recondicionamento, acondicionamento, pintura, beneficiamento, lavagem, secagem, tingimento, galvanoplastia, anodização, corte, recorte, plastificação, costura, acabamento, polimento e congêneres de objetos quaisquer.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('fe25fe4f-d614-46ee-9ea3-f72d3f4ee4df', 'NBS', '140601', 'Instalação e montagem de aparelhos, máquinas e equipamentos, inclusive montagem industrial, prestados ao usuário final, exclusivamente com material por ele fornecido.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2c9fee61-5ca7-462b-b6e0-ff827f51f175', 'NBS', '140701', 'Colocação de molduras e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('129d54ae-7e8b-49aa-b862-8871a0a23c9f', 'NBS', '140801', 'Encadernação, gravação e douração de livros, revistas e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('806af146-1c2a-41bd-874c-a93362b430a8', 'NBS', '140901', 'Alfaiataria e costura, quando o material for fornecido pelo usuário final, exceto aviamento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c1832b43-c630-46f6-88c5-abf4b3460e6a', 'NBS', '141001', 'Tinturaria e lavanderia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('dceeeac2-df5a-4b56-9b54-8c44e028c31a', 'NBS', '141101', 'Tapeçaria e reforma de estofamentos em geral.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0a18a521-e70e-4541-aeda-647d73d4927a', 'NBS', '141201', 'Funilaria e lanternagem.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8cc43605-cdec-4c07-ac96-ff6463bbc6bf', 'NBS', '141301', 'Carpintaria.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ed915f9a-4797-4f4e-bf75-3ddf998dd7d1', 'NBS', '141302', 'Serralheria.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9ae3ce95-bfe0-49d7-8d26-8d148592a4e6', 'NBS', '141401', 'Guincho intramunicipal.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('4aff7244-fc92-4f4e-8379-e40c5c41783a', 'NBS', '141402', 'Guindaste e içamento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a680b230-b8d4-48fa-8d67-b43fcf8fd61a', 'NBS', '150101', 'Administração de fundos quaisquer e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5ed57b6d-8545-4f24-b463-0c2c3ab36464', 'NBS', '150102', 'Administração de consórcio e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b861e569-1ccc-4a7a-9930-8f57252e01bf', 'NBS', '150103', 'Administração de cartão de crédito ou débito e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('be5087bd-48ac-4a30-8890-5286fa11dcd8', 'NBS', '150104', 'Administração de carteira de clientes e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1ecec629-f5db-4e86-a527-dfdea6e8e99e', 'NBS', '150105', 'Administração de cheques pré-datados e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3aa49216-4dd0-4074-adcf-314485de3f56', 'NBS', '150201', 'Abertura de conta-corrente no País, bem como a manutenção da referida conta ativa e inativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('24ee093f-3ed5-4bce-8313-99e98a869fcd', 'NBS', '150202', 'Abertura de conta-corrente no exterior, bem como a manutenção da referida conta ativa e inativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('031c7e9e-9cca-43e8-927b-ccb847676504', 'NBS', '150203', 'Abertura de conta de investimentos e aplicação no País, bem como a manutenção da referida conta ativa e inativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('abc58766-6c5e-4fdb-9fc1-e68dd201e897', 'NBS', '150204', 'Abertura de conta de investimentos e aplicação no exterior, bem como a manutenção da referida conta ativa e inativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f41012c4-d2c2-4f4f-a923-3a6834ffd753', 'NBS', '150205', 'Abertura de caderneta de poupança no País, bem como a manutenção da referida conta ativa e inativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1352aebc-16fd-4c1e-a331-862e44cd458e', 'NBS', '150206', 'Abertura de caderneta de poupança no exterior, bem como a manutenção da referida conta ativa e inativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d2c3a7e7-aa41-4a39-8dec-4047350e3bd4', 'NBS', '150207', 'Abertura de contas em geral no País, não abrangida em outro subitem, bem como a manutenção das referidas contas ativas e inativas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3e64f8d6-87af-4465-bd8c-6186dd62cdd3', 'NBS', '150208', 'Abertura de contas em geral no exterior, não abrangida em outro subitem, bem como a manutenção das referidas contas ativas e inativas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c0b63c7c-e72e-490c-a43a-915d09e0c9f7', 'NBS', '150301', 'Locação de cofres particulares.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('381d96e3-cc61-4bc3-b06a-9cd65508d6a7', 'NBS', '150302', 'Manutenção de cofres particulares.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f7d197f6-0e24-42a7-a964-5c166c9f59fe', 'NBS', '150303', 'Locação de terminais eletrônicos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ca390f15-285e-406d-a1aa-725781366d88', 'NBS', '150304', 'Manutenção de terminais eletrônicos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('261b4f22-c5c9-4b81-a987-5bfb4c05f7e7', 'NBS', '150305', 'Locação de terminais de atendimento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('af17f484-54b2-4230-aaf8-9f642ecf4e1c', 'NBS', '150306', 'Manutenção de terminais de atendimento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f17d40ba-f353-43e1-9137-54021618cfef', 'NBS', '150307', 'Locação de bens e equipamentos em geral.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7763f48f-7081-4f5d-abf5-858429413ec5', 'NBS', '150308', 'Manutenção de bens e equipamentos em geral.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8c21fd40-894e-42b8-a8a6-bfae730a3fc6', 'NBS', '150401', 'Fornecimento ou emissão de atestados em geral, inclusive atestado de idoneidade, atestado de capacidade financeira e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7a83b99e-1523-4ad2-b014-93fefda85363', 'NBS', '150501', 'Cadastro, elaboração de ficha cadastral, renovação cadastral e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b8386ae8-bf6f-4b0b-a03f-6e668a85d7ac', 'NBS', '150502', 'Inclusão no Cadastro de Emitentes de Cheques sem Fundos - CCF.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9b808f87-d070-4d8e-aea3-f7219c60c202', 'NBS', '150503', 'Exclusão no Cadastro de Emitentes de Cheques sem Fundos - CCF.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('608b5f17-226d-417d-8f8c-b16b278eb65f', 'NBS', '150504', 'Inclusão em quaisquer outros bancos cadastrais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2b856686-921e-448a-b194-4f71b5755618', 'NBS', '150505', 'Exclusão em quaisquer outros bancos cadastrais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2a7d8621-86ea-4394-b0b4-d5d7d6fb68c1', 'NBS', '150601', 'Emissão, reemissão e fornecimento de avisos, comprovantes e documentos em geral', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f84375c4-9973-4030-be8f-8cf081663070', 'NBS', '150602', 'Abono de firmas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e7b8f29b-4428-4f7c-8466-cdba3e24a209', 'NBS', '150603', 'Coleta e entrega de documentos, bens e valores.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b20ca641-0266-49e9-8de6-5b48f8071322', 'NBS', '150604', 'Comunicação com outra agência ou com a administração central.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ed7be1eb-18e0-469b-8aa4-42b2a7360db6', 'NBS', '150605', 'Licenciamento eletrônico de veículos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0e8d25e9-9f36-4ff3-bc88-8b1bb3c2aa7b', 'NBS', '150606', 'Transferência de veículos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b8ef7a49-1884-46ec-89f7-d6b3561c8d43', 'NBS', '150607', 'Agenciamento fiduciário ou depositário.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7e0a4782-1ccb-4f0a-b821-06745fbf8ab4', 'NBS', '150608', 'Devolução de bens em custódia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('068e3fbf-0a64-486a-9af8-ce388a18ff5c', 'NBS', '150701', 'Acesso, movimentação, atendimento e consulta a contas em geral, por qualquer meio ou processo, inclusive por telefone, fac-símile, internet e telex.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c7a2ae30-ab20-48ae-a4a5-aca5c3f8c048', 'NBS', '150702', 'Acesso a terminais de atendimento, inclusive vinte e quatro horas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d87b98d8-5a23-469e-8c9f-3006852d6ab9', 'NBS', '150703', 'Acesso a outro banco e à rede compartilhada.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a66fdfa0-c5fd-4be9-9cb8-12ff9a252947', 'NBS', '150704', 'Fornecimento de saldo, extrato e demais informações relativas a contas em geral, por qualquer meio ou processo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e390ce09-e15b-41b4-8cfb-9a89c11aa410', 'NBS', '150801', 'Emissão, reemissão, alteração, cessão, substituição, cancelamento e registro de contrato de crédito.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b31160de-688a-490c-9a71-3c1c1fbb99d2', 'NBS', '150802', 'Estudo, análise e avaliação de operações de crédito.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c7a0bcb0-36f6-4f92-a6a3-01b838f59206', 'NBS', '150803', 'Emissão, concessão, alteração ou contratação de aval, fiança, anuência e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('731f8db0-8a8f-4f87-b155-c6d80bad75a2', 'NBS', '150804', 'Serviços relativos à abertura de crédito, para quaisquer fins.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('4c44847d-d03f-459e-ba69-4881a76eec6e', 'NBS', '150901', 'Arrendamento mercantil (leasing) de quaisquer bens, inclusive cessão de direitos e obrigações, substituição de garantia, alteração, cancelamento e registro de contrato, e demais serviços relacionados ao arrendamento mercantil (leasing).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('66cebe22-d38d-4e73-9a38-e4bac0d72589', 'NBS', '151001', 'Serviços relacionados a cobranças em geral, de títulos quaisquer, de contas ou carnês, de câmbio, de tributos e por conta de terceiros, inclusive os efetuados por meio eletrônico, automático ou por máquinas de atendimento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e64d4946-58ab-4a97-9e78-5c7cdc78ec46', 'NBS', '151002', 'Serviços relacionados a recebimentos em geral, de títulos quaisquer, de contas ou carnês, de câmbio, de tributos e por conta de terceiros, inclusive os efetuados por meio eletrônico, automático ou por máquinas de atendimento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ecd2f729-2f85-4b38-a0ed-81cf6223bdf5', 'NBS', '151003', 'Serviços relacionados a pagamentos em geral, de títulos quaisquer, de contas ou carnês, de câmbio, de tributos e por conta de terceiros, inclusive os efetuados por meio eletrônico, automático ou por máquinas de atendimento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('2c056a4f-f399-40d2-8c52-5f74b5187fd9', 'NBS', '151004', 'Serviços relacionados a fornecimento de posição de cobrança, recebimento ou pagamento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5373aaee-ad8d-4b2d-a8df-1ad64d4bb76c', 'NBS', '151005', 'Serviços relacionados a emissão de carnês, fichas de compensação, impressos e documentos em geral.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ec570c9f-6a45-45b3-9353-52ec8dfa15d6', 'NBS', '151101', 'Devolução de títulos, protesto de títulos, sustação de protesto, manutenção de títulos, reapresentação de títulos, e demais serviços a eles relacionados.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('12bf90c5-76d3-41b8-9f5e-7a396a33ca40', 'NBS', '151201', 'Custódia em geral, inclusive de títulos e valores mobiliários.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1d48bb7e-63d2-4a17-a5c0-86985111b079', 'NBS', '151301', 'Serviços relacionados a operações de câmbio em geral, edição, alteração, prorrogação, cancelamento e baixa de contrato de câmbio.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f39084d1-9d27-4ff3-b5d9-2cbe120b893e', 'NBS', '151302', 'Serviços relacionados a emissão de registro de exportação ou de crédito.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('16aa91f4-4c8f-49fc-87cb-bbde23fcbf40', 'NBS', '151303', 'Serviços relacionados a cobrança ou depósito no exterior.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cef74cc9-62b6-4ada-85be-53f865af565f', 'NBS', '151304', 'Serviços relacionados a emissão, fornecimento e cancelamento de cheques de viagem.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('58286781-1bbb-4ca2-b07f-c839344800d1', 'NBS', '151305', 'Serviços relacionados a fornecimento, transferência, cancelamento e demais serviços relativos a carta de crédito de importação, exportação e garantias recebidas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0f212759-0151-4fa3-9dc9-8ebfc2fa4fa5', 'NBS', '151306', 'Serviços relacionados a envio e recebimento de mensagens em geral relacionadas a operações de câmbio.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ef91915c-3ad5-41fe-9b92-16d077b20971', 'NBS', '151401', 'Fornecimento, emissão, reemissão de cartão magnético, cartão de crédito, cartão de débito, cartão salário e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c0ccaa26-759b-44a3-99e4-702f24d205b6', 'NBS', '151402', 'Renovação de cartão magnético, cartão de crédito, cartão de débito, cartão salário e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('98008bf7-b052-46a8-af21-d6931b2aae24', 'NBS', '151403', 'Manutenção de cartão magnético, cartão de crédito, cartão de débito, cartão salário e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ae2c50f5-a470-4822-8f67-ecd25ee03f57', 'NBS', '151501', 'Compensação de cheques e títulos quaisquer.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c4098a68-d8b9-4ae0-a7b6-b784da5db084', 'NBS', '151502', 'Serviços relacionados a depósito, inclusive depósito identificado, a saque de contas quaisquer, por qualquer meio ou processo, inclusive em terminais eletrônicos e de atendimento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6ef90feb-f114-4fdc-ae6c-a65da8dd04a2', 'NBS', '151601', 'Emissão, reemissão, liquidação, alteração, cancelamento e baixa de ordens de pagamento, ordens de crédito e similares, por qualquer meio ou processo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c29eee46-934f-4bd4-b188-eeae6477938f', 'NBS', '151602', 'Serviços relacionados à transferência de valores, dados, fundos, pagamentos e similares, inclusive entre contas em geral.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e31f485d-c414-4611-95f0-914ddc947b5a', 'NBS', '151701', 'Emissão e fornecimento de cheques quaisquer, avulso ou por talão.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b95b65ec-bb9b-47aa-b601-80549b7a82ef', 'NBS', '151702', 'Devolução de cheques quaisquer, avulso ou por talão.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('baa35191-a523-4a07-839c-6ac29bf39b8c', 'NBS', '151703', 'Sustação, cancelamento e oposição de cheques quaisquer, avulso ou por talão.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c9015ecd-58b4-448b-896e-d8f50c5d6312', 'NBS', '151801', 'Serviços relacionados a crédito imobiliário, de avaliação e vistoria de imóvel ou obra.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a4e4fb6e-2808-41a7-bdd0-d698d8539b0f', 'NBS', '151802', 'Serviços relacionados a crédito imobiliário, de análise técnica e jurídica.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b87d5b5c-9bd6-425d-a037-d20d1e73542e', 'NBS', '151803', 'Serviços relacionados a crédito imobiliário, de emissão, reemissão, alteração, transferência e renegociação de contrato.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('06438847-bbd4-46a7-8ffd-7ebf39fd80bb', 'NBS', '151804', 'Serviços relacionados a crédito imobiliário, de emissão e reemissão do termo de quitação.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ff955ed4-a1a5-45cc-bb01-6ec4610ef869', 'NBS', '151805', 'Demais serviços relacionados a crédito imobiliário.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('19db3c83-2055-4f0b-910d-093b829bde60', 'NBS', '160101', 'Serviços de transporte coletivo municipal rodoviário de passageiros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('778132b3-30a8-4327-b4bd-be57598fd08b', 'NBS', '160102', 'Serviços de transporte coletivo municipal metroviário de passageiros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d1561045-bbde-4272-b624-203698acc5f1', 'NBS', '160103', 'Serviços de transporte coletivo municipal ferroviário de passageiros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('bf490d4f-a7b9-4df6-bf7d-e77972759c9e', 'NBS', '160104', 'Serviços de transporte coletivo municipal aquaviário de passageiros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('45dc104b-a860-452f-b7b2-c855be714ea5', 'NBS', '160201', 'Outros serviços de transporte de natureza municipal.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e6d2b8d7-01ce-4165-a7a5-319cb0d9b811', 'NBS', '170101', 'Assessoria ou consultoria de qualquer natureza, não contida em outros itens desta lista.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('85576298-29f8-4c29-8011-b8349c604dff', 'NBS', '170102', 'Análise, exame, pesquisa, coleta, compilação e fornecimento de dados e informações de qualquer natureza, inclusive cadastro e similares.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9f1af39f-bcd1-4d0e-9f58-d088f5d50dd3', 'NBS', '170201', 'Datilografia, digitação, estenografia e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a3f5ef68-13a8-4487-9819-a8247718b014', 'NBS', '170202', 'Expediente, secretaria em geral, apoio e infra-estrutura administrativa e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e76b2acb-b37e-4e81-97af-5200f80987b1', 'NBS', '170203', 'Resposta audível e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1f261907-7f1e-4ef9-923f-ee048affe737', 'NBS', '170204', 'Redação, edição, revisão e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('84ef2a28-3edc-4947-adfa-d7c8ebbffef4', 'NBS', '170205', 'Interpretação, tradução e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('edf5a569-48cd-4e51-b7f2-cc39c297a2ca', 'NBS', '170301', 'Planejamento, coordenação, programação ou organização técnica.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('64786668-37d3-4d62-a11d-2fbcfbb1fb4e', 'NBS', '170302', 'Planejamento, coordenação, programação ou organização financeira.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('dd8fc1ce-5b0d-4c93-86db-9506b3735c1e', 'NBS', '170303', 'Planejamento, coordenação, programação ou organização administrativa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('94f3ea57-55c0-4f68-96fc-abf7957e3ff2', 'NBS', '170401', 'Recrutamento, agenciamento, seleção e colocação de mão-de-obra.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('ad4e030c-3a3c-48b7-bc2e-120edf85b7e9', 'NBS', '170501', 'Fornecimento de mão-de-obra, mesmo em caráter temporário, inclusive de empregados ou trabalhadores, avulsos ou temporários, contratados pelo prestador de serviço.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('63a8550d-3c8b-45c5-8fd6-a8dbc5a1eeaa', 'NBS', '170601', 'Propaganda e publicidade, inclusive promoção de vendas, planejamento de campanhas ou sistemas de publicidade, elaboração de desenhos, textos e demais materiais publicitários.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('080d47a4-257d-420a-bb21-b94e3a7aa604', 'NBS', '170801', 'Franquia (franchising).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3c962edf-14c5-4caf-a922-66d5ab30a37a', 'NBS', '170901', 'Perícias, laudos, exames técnicos e análises técnicas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d9bc348b-d8ba-4c7c-8318-ed3dac5ee6ad', 'NBS', '171001', 'Planejamento, organização e administração de feiras, exposições, e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('bf256a0c-4676-474b-9895-17f99bc62d52', 'NBS', '171002', 'Planejamento, organização e administração de congressos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('90cd3ce5-d836-40cc-97a5-045793e640bf', 'NBS', '171101', 'Organização de festas e recepções.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9461d634-4ae6-463c-b260-97d4d9af5321', 'NBS', '171102', 'Bufê (exceto o fornecimento de alimentação e bebidas, que fica sujeito ao ICMS).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('fb429b1b-155b-47b7-b922-10f9f864ba9d', 'NBS', '171201', 'Administração em geral, inclusive de bens e negócios de terceiros.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cd299eca-3059-4a2b-a7b5-7e98e22e680f', 'NBS', '171301', 'Leilão e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7ab769bd-71a4-4e56-8dad-a6196d7ca167', 'NBS', '171401', 'Advocacia', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('3063a6b9-0d26-4306-8e7a-826035c6cfc3', 'NBS', '171501', 'Arbitragem de qualquer espécie, inclusive jurídica.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('88dbb912-4a88-4090-aaa9-a1d25d7681a3', 'NBS', '171601', 'Auditoria.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a192ce43-5ea0-4bbc-88cb-b3e7e27f278a', 'NBS', '171701', 'Análise de Organização e Métodos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('38255417-a5c5-4e4d-bbeb-2148649f2140', 'NBS', '171801', 'Atuária e cálculos técnicos de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e1cdaa2a-2971-4e45-a049-f521ee170b8b', 'NBS', '171901', 'Contabilidade, inclusive serviços técnicos e auxiliares.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c8d13cab-0789-451d-a91d-ce1e07ffd962', 'NBS', '172001', 'Consultoria e assessoria econômica ou financeira.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('97c689f1-1b0d-4dc2-9224-c3067a13894f', 'NBS', '172101', 'Estatística.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('64e8d54d-a30f-412c-ac7c-81983f0ddf91', 'NBS', '172201', 'Cobrança em geral.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('adbfc8e6-b14e-4b5a-930e-d2f538c6d33a', 'NBS', '172301', 'Assessoria, análise, avaliação, atendimento, consulta, cadastro, seleção, gerenciamento de informações, administração de contas a receber ou a pagar e em geral, relacionados a operações de faturização (factoring).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8505f682-3d9d-4caa-88a2-49ab12fdf0e2', 'NBS', '172401', 'Apresentação de palestras, conferências, seminários e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6aa7b295-89aa-4062-ac80-8bbb3e6c744c', 'NBS', '172501', 'Inserção de textos, desenhos e outros materiais de propaganda e publicidade, em qualquer meio (exceto em livros, jornais, periódicos e nas modalidades de serviços de radiodifusão sonora e de sons e imagens de recepção livre e gratuita).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d7d4a62f-da5e-4334-8f5a-eb813569278c', 'NBS', '180101', 'Serviços de regulação de sinistros vinculados a contratos de seguros e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('d131f264-83bd-4c81-a7bc-e2c4e2990f66', 'NBS', '180102', 'Serviços de inspeção e avaliação de riscos para cobertura de contratos de seguros e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1b1fd220-9124-46d4-9a7a-c4103ecfa10a', 'NBS', '180103', 'Serviços de prevenção e gerência de riscos seguráveis e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('298bb1a0-145d-4f36-bec0-666da7ee4da2', 'NBS', '190101', 'Serviços de distribuição e venda de bilhetes e demais produtos de loteria, cartões, pules ou cupons de apostas, sorteios, prêmios, inclusive os decorrentes de títulos de capitalização e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9fd2f503-e8c5-4b82-bf76-557941eb9059', 'NBS', '190102', 'Serviços de distribuição e venda de bingos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cf935e05-9ae7-49a4-af4a-b0e72d4e071c', 'NBS', '200101', 'Serviços portuários, ferroportuários, utilização de porto, movimentação de passageiros, reboque de embarcações, rebocador escoteiro, atracação, desatracação, serviços de praticagem, capatazia, armazenagem de qualquer natureza, serviços acessórios, movimentação de mercadorias, serviços de apoio marítimo, de movimentação ao largo, serviços de armadores, estiva, conferência, logística e congêneres. (prestado em terra)', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('5d53dce6-b43c-4591-acc2-689f4d7dbf57', 'NBS', '200102', 'Serviços portuários, ferroportuários, utilização de porto, movimentação de passageiros, reboque de embarcações, rebocador escoteiro, atracação, desatracação, serviços de praticagem, capatazia, armazenagem de qualquer natureza, serviços acessórios, movimentação de mercadorias, serviços de apoio marítimo, de movimentação ao largo, serviços de armadores, estiva, conferência, logística e congêneres. (prestado em águas marinhas)', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('804eb3a8-93bb-43d6-873c-4d43b9142be4', 'NBS', '200201', 'Serviços aeroportuários, utilização de aeroporto, movimentação de passageiros, armazenagem de qualquer natureza, capatazia, movimentação de aeronaves, serviços de apoio aeroportuários, serviços acessórios, movimentação de mercadorias, logística e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('e9692648-74ab-4ef7-a8f1-2a9819a463ec', 'NBS', '200301', 'Serviços de terminais rodoviários, ferroviários, metroviários, movimentação de passageiros, mercadorias, inclusive suas operações, logística e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a3b4b578-0dbf-44fb-a3eb-593ba3ded7f7', 'NBS', '210101', 'Serviços de registros públicos, cartorários e notariais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('7c1588dd-346e-4bd1-88a5-69171a7feca8', 'NBS', '220101', 'Serviços de exploração de rodovia mediante cobrança de preço ou pedágio dos usuários, envolvendo execução de serviços de conservação, manutenção, melhoramentos para adequação de capacidade e segurança de trânsito, operação, monitoração, assistência aos usuários e outros serviços definidos em contratos, atos de concessão ou de permissão ou em normas oficiais.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f5196e7f-005d-445b-b165-07b01e71d3b2', 'NBS', '230101', 'Serviços de programação e comunicação visual e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('49b60007-ccfd-4327-b07b-6d95744187ca', 'NBS', '230102', 'Serviços de desenho industrial e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b67a7121-ef8f-4d3c-a60c-578b55c68e71', 'NBS', '240101', 'Serviços de chaveiros, confecção de carimbos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('91927567-5f16-4f71-b0eb-cc2892bebc3b', 'NBS', '240102', 'Serviços de placas, sinalização visual, banners, adesivos e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9b40e39b-6f2c-42f0-bd20-093f906b5a93', 'NBS', '250101', 'Funerais, inclusive fornecimento de caixão, urna ou esquifes; aluguel de capela; transporte do corpo cadavérico; fornecimento de flores, coroas e outros paramentos; desembaraço de certidão de óbito; fornecimento de véu, essa e outros adornos; embalsamento, embelezamento, conservação ou restauração de cadáveres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b0fc5014-28ee-47a2-8557-d1c3204ab299', 'NBS', '250201', 'Translado intramunicipal de corpos e partes de corpos cadavéricos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('0352bece-e3b4-4f42-a3cf-2d8ed68a9bc9', 'NBS', '250202', 'Cremação de corpos e partes de corpos cadavéricos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a012c6d7-14e5-4722-b503-a4f35f417ece', 'NBS', '250301', 'Planos ou convênio funerários.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8842c072-8748-4761-a95a-189b9f3ae0b2', 'NBS', '250401', 'Manutenção e conservação de jazigos e cemitérios.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('cbc67e9f-1469-409d-88f8-efe1d847948c', 'NBS', '250501', 'Cessão de uso de espaços em cemitérios para sepultamento.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6fe8b004-be02-4cb3-a19c-200c823782af', 'NBS', '260101', 'Serviços de coleta, remessa ou entrega de correspondências, documentos, objetos, bens ou valores, inclusive pelos correios e suas agências franqueadas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('575b07c2-d37e-44e8-93ed-4fa44f4cbf7b', 'NBS', '260102', 'Serviços de courrier e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('a8613410-04dd-4306-bd81-0a3dc939ee6d', 'NBS', '270101', 'Serviços de assistência social.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8757d402-1cff-4a00-8914-a333bf9f0c92', 'NBS', '280101', 'Serviços de avaliação de bens e serviços de qualquer natureza.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c7cb274a-9e95-445f-aac8-7ea415207a80', 'NBS', '290101', 'Serviços de biblioteconomia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('48de48fc-b3ef-4061-ad49-010f00eb3c30', 'NBS', '300101', 'Serviços de biologia e biotecnologia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('4746716f-ec55-4a6d-9fda-c71538b310a1', 'NBS', '300102', 'Serviços de química.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('9ac75280-c322-4ddb-98db-71895b4266e6', 'NBS', '310101', 'Serviços técnicos em edificações e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('b2e76a8d-8ea8-41c8-891f-f1d8da545a7d', 'NBS', '310102', 'Serviços técnicos em eletrônica, eletrotécnica e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('774bda55-f240-4811-9297-aa29f8606a38', 'NBS', '310103', 'Serviços técnicos em mecânica e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('8742ca8c-86ec-475c-90ad-15eec9cbf35e', 'NBS', '310104', 'Serviços técnicos em telecomunicações e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('38a9771b-c522-4e39-97d4-11aa0973e1df', 'NBS', '320101', 'Serviços de desenhos técnicos.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('212752f7-a11f-4eaa-b718-bd857e22290c', 'NBS', '330101', 'Serviços de desembaraço aduaneiro, comissários, despachantes e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('6d29599d-ea22-4d13-9bc1-7f0d3851197b', 'NBS', '340101', 'Serviços de investigações particulares, detetives e congêneres.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('815cb4e5-1801-4043-823d-8dd4ddaf57b6', 'NBS', '350101', 'Serviços de reportagem e jornalismo.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('1f8819e2-2aa7-4e17-8bfa-cbc9a8e7c39e', 'NBS', '350102', 'Serviços de assessoria de imprensa.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('acf385c9-aba8-4552-9558-05d78c1836d1', 'NBS', '350103', 'Serviços de relações públicas.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('510edd0e-3110-4d94-b13e-ff92e4578cb7', 'NBS', '360101', 'Serviços de meteorologia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('407cf5bc-a728-49fb-8c6f-837263e9d2a2', 'NBS', '370101', 'Serviços de artistas, atletas, modelos e manequins.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('906d58f7-5ce8-4689-99a8-ffb75292a57a', 'NBS', '380101', 'Serviços de museologia.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('00633831-26b6-4570-bb6d-452a1f97b47c', 'NBS', '390101', 'Serviços de ourivesaria e lapidação (quando o material for fornecido pelo tomador do serviço).', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('f4b05c62-f692-4620-846c-dfbdd29acd28', 'NBS', '400101', 'Obras de arte sob encomenda.', DATE '2026-03-03', 'ACTIVE', NOW()),
  ('c8c2795a-ef1b-42f5-8f82-88165a587208', 'NBS', '990101', 'Serviços sem a incidência de ISSQN e ICMS', DATE '2026-03-03', 'ACTIVE', NOW())
ON CONFLICT (code_type, code_value, valid_from) DO UPDATE
SET
  description = EXCLUDED.description,
  status = EXCLUDED.status;


-- <<< END V18__fiscal_code_catalog_nfse_nbs.sql

-- >>> BEGIN V19__nfse_nbs_codes_table.sql

CREATE TABLE IF NOT EXISTS nfse_nbs_codes (
  id UUID PRIMARY KEY,
  code_value VARCHAR(6) NOT NULL,
  description TEXT NOT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  source_url VARCHAR(500) NOT NULL,
  source_hash VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_nbs_code_value_digits CHECK (code_value ~ '^[0-9]{6}$'),
  CONSTRAINT ck_nfse_nbs_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_nbs_codes_value_valid_from
  ON nfse_nbs_codes (code_value, valid_from);

CREATE INDEX IF NOT EXISTS idx_nfse_nbs_codes_status_value
  ON nfse_nbs_codes (status, code_value);

INSERT INTO nfse_nbs_codes (
  id,
  code_value,
  description,
  valid_from,
  valid_to,
  status,
  source_url,
  source_hash,
  created_at,
  updated_at
)
SELECT
  f.id,
  f.code_value,
  f.description,
  f.valid_from,
  f.valid_to,
  CASE
    WHEN COALESCE(f.status, 'ACTIVE') = 'ACTIVE' THEN 'ACTIVE'
    ELSE 'INACTIVE'
  END,
  'https://www.gov.br/nfse/pt-br/mei-e-demais-empresas/codigos-de-tributacao-nacional-nbs',
  md5(COALESCE(f.code_value, '') || '|' || COALESCE(f.description, '') || '|' || COALESCE(f.valid_from::text, '')),
  NOW(),
  NOW()
FROM fiscal_code_catalog f
WHERE f.code_type = 'NBS'
ON CONFLICT (code_value, valid_from) DO UPDATE
SET
  description = EXCLUDED.description,
  valid_to = EXCLUDED.valid_to,
  status = EXCLUDED.status,
  source_url = EXCLUDED.source_url,
  source_hash = EXCLUDED.source_hash,
  updated_at = NOW();

-- <<< END V19__nfse_nbs_codes_table.sql

-- >>> BEGIN V20__nfse_core_tables.sql

CREATE TABLE IF NOT EXISTS nfse_configs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  ambiente VARCHAR(20) NOT NULL,
  municipio_codigo_ibge VARCHAR(7) NOT NULL,
  provedor VARCHAR(40) NOT NULL,
  serie_rps VARCHAR(10) NOT NULL,
  aliquota_iss_padrao NUMERIC(7,4) NOT NULL,
  item_lista_servico_padrao VARCHAR(10) NOT NULL,
  codigo_tributacao_municipio VARCHAR(30),
  emission_mode VARCHAR(20) NOT NULL,
  emit_for_cpf_mode VARCHAR(30) NOT NULL,
  auto_issue_on_appointment_close BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_configs_ambiente CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO')),
  CONSTRAINT ck_nfse_configs_emission_mode CHECK (emission_mode IN ('MANUAL', 'ASK_ON_CLOSE', 'AUTO_ON_CLOSE')),
  CONSTRAINT ck_nfse_configs_emit_for_cpf_mode CHECK (emit_for_cpf_mode IN ('ALWAYS', 'ASK', 'NEVER_AUTO'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_configs_tenant_ambiente
  ON nfse_configs (tenant_id, ambiente);

CREATE INDEX IF NOT EXISTS idx_nfse_configs_tenant_ambiente_municipio
  ON nfse_configs (tenant_id, ambiente, municipio_codigo_ibge);

CREATE TABLE IF NOT EXISTS nfse_invoices (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  appointment_id UUID,
  customer_type VARCHAR(10) NOT NULL,
  customer_document VARCHAR(20),
  customer_country_code VARCHAR(3),
  customer_document_type VARCHAR(30),
  customer_name VARCHAR(255) NOT NULL,
  customer_email VARCHAR(255),
  customer_phone VARCHAR(20),
  fiscal_status VARCHAR(30) NOT NULL,
  operational_status VARCHAR(40),
  municipio_codigo_ibge VARCHAR(7) NOT NULL,
  provedor VARCHAR(40) NOT NULL,
  ambiente VARCHAR(20) NOT NULL,
  numero_rps BIGINT NOT NULL,
  serie_rps VARCHAR(10) NOT NULL,
  numero_nfse VARCHAR(30),
  codigo_verificacao VARCHAR(100),
  protocolo VARCHAR(100),
  data_competencia DATE NOT NULL,
  data_emissao TIMESTAMPTZ,
  natureza_operacao VARCHAR(100) NOT NULL,
  item_lista_servico VARCHAR(10) NOT NULL,
  codigo_tributacao_municipio VARCHAR(30),
  valor_servicos NUMERIC(15,2) NOT NULL,
  valor_deducoes NUMERIC(15,2) NOT NULL DEFAULT 0,
  valor_iss NUMERIC(15,2) NOT NULL,
  aliquota_iss NUMERIC(7,4) NOT NULL,
  iss_retido BOOLEAN NOT NULL DEFAULT FALSE,
  xml_envio_enc BYTEA,
  xml_retorno_enc BYTEA,
  pdf_storage_key VARCHAR(500),
  pdf_generated_at TIMESTAMPTZ,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_invoices_customer_type CHECK (customer_type IN ('CPF', 'CNPJ', 'EXTERIOR')),
  CONSTRAINT ck_nfse_invoices_ambiente CHECK (ambiente IN ('HOMOLOGACAO', 'PRODUCAO')),
  CONSTRAINT ck_nfse_invoices_fiscal_status CHECK (fiscal_status IN (
    'DRAFT','READY_TO_SEND','SIGNED','SUBMITTED','PENDING','AUTHORIZED','REJECTED','CANCEL_PENDING','CANCELLED','CANCEL_REJECTED'
  )),
  CONSTRAINT ck_nfse_invoices_operational_status CHECK (operational_status IS NULL OR operational_status IN (
    'PENDING_PASSWORD_UNLOCK','PROCESSING_PDF','PDF_ERROR','WAITING_PROVIDER','RETRY_SCHEDULED'
  )),
  CONSTRAINT ck_nfse_invoices_customer_document_rule CHECK (
    (customer_type = 'EXTERIOR' AND customer_country_code IS NOT NULL)
    OR
    (customer_type IN ('CPF', 'CNPJ') AND customer_document IS NOT NULL)
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_invoices_tenant_municipio_serie_numero_ambiente
  ON nfse_invoices (tenant_id, municipio_codigo_ibge, serie_rps, numero_rps, ambiente);

CREATE INDEX IF NOT EXISTS idx_nfse_invoices_tenant_status_created
  ON nfse_invoices (tenant_id, fiscal_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_nfse_invoices_tenant_appointment
  ON nfse_invoices (tenant_id, appointment_id);

CREATE INDEX IF NOT EXISTS idx_nfse_invoices_tenant_protocolo
  ON nfse_invoices (tenant_id, protocolo);

CREATE INDEX IF NOT EXISTS idx_nfse_invoices_tenant_numero_nfse
  ON nfse_invoices (tenant_id, numero_nfse);

CREATE TABLE IF NOT EXISTS nfse_invoice_items (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id UUID NOT NULL REFERENCES nfse_invoices(id) ON DELETE CASCADE,
  line_number INT NOT NULL,
  descricao_servico VARCHAR(500) NOT NULL,
  quantidade NUMERIC(15,4) NOT NULL,
  valor_unitario NUMERIC(15,4) NOT NULL,
  valor_total NUMERIC(15,2) NOT NULL,
  item_lista_servico VARCHAR(10) NOT NULL,
  codigo_tributacao_municipio VARCHAR(30),
  aliquota_iss NUMERIC(7,4) NOT NULL,
  valor_iss NUMERIC(15,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_invoice_items_quantidade_pos CHECK (quantidade > 0),
  CONSTRAINT ck_nfse_invoice_items_valor_unitario_nonneg CHECK (valor_unitario >= 0),
  CONSTRAINT ck_nfse_invoice_items_valor_total_nonneg CHECK (valor_total >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_invoice_items_invoice_line
  ON nfse_invoice_items (invoice_id, line_number);

CREATE INDEX IF NOT EXISTS idx_nfse_invoice_items_tenant_invoice
  ON nfse_invoice_items (tenant_id, invoice_id);

CREATE TABLE IF NOT EXISTS nfse_invoice_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id UUID NOT NULL REFERENCES nfse_invoices(id) ON DELETE CASCADE,
  event_type VARCHAR(40) NOT NULL,
  event_status VARCHAR(20) NOT NULL,
  provider_code VARCHAR(30),
  provider_message TEXT,
  payload_hash VARCHAR(128),
  requested_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_nfse_invoice_events_tenant_invoice_created
  ON nfse_invoice_events (tenant_id, invoice_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_nfse_invoice_events_tenant_type_created
  ON nfse_invoice_events (tenant_id, event_type, created_at DESC);

CREATE TABLE IF NOT EXISTS nfse_idempotency_requests (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  operation VARCHAR(80) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  response_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_idempotency_request
  ON nfse_idempotency_requests (tenant_id, operation, idempotency_key);

CREATE TABLE IF NOT EXISTS nfse_provider_capabilities (
  id UUID PRIMARY KEY,
  municipio_codigo_ibge VARCHAR(7) NOT NULL,
  provedor VARCHAR(40) NOT NULL,
  layout_version VARCHAR(20) NOT NULL,
  cancel_supported BOOLEAN NOT NULL,
  cancel_window_hours INT,
  cancel_mode VARCHAR(20) NOT NULL,
  accepted_cancel_reason_codes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_provider_capabilities_cancel_mode CHECK (cancel_mode IN ('SYNC', 'ASYNC')),
  CONSTRAINT ck_nfse_provider_capabilities_cancel_window CHECK (
    (cancel_supported = FALSE AND cancel_window_hours IS NULL)
    OR
    (cancel_supported = TRUE AND cancel_window_hours IS NOT NULL AND cancel_window_hours > 0)
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_provider_cap_municipio_provedor_layout
  ON nfse_provider_capabilities (municipio_codigo_ibge, provedor, layout_version);

CREATE INDEX IF NOT EXISTS idx_nfse_provider_cap_municipio_provedor
  ON nfse_provider_capabilities (municipio_codigo_ibge, provedor);

-- <<< END V20__nfse_core_tables.sql

-- >>> BEGIN V21__nfse_pdf_jobs_and_unlock.sql

CREATE TABLE IF NOT EXISTS nfse_pdf_jobs (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id UUID NOT NULL REFERENCES nfse_invoices(id) ON DELETE CASCADE,
  requested_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  status VARCHAR(20) NOT NULL,
  pdf_storage_key VARCHAR(500),
  download_count INT NOT NULL DEFAULT 0,
  downloaded_at TIMESTAMPTZ,
  download_expires_at TIMESTAMPTZ,
  error_code VARCHAR(80),
  error_message TEXT,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at TIMESTAMPTZ,
  CONSTRAINT ck_nfse_pdf_jobs_status CHECK (status IN ('QUEUED', 'PROCESSING', 'DONE', 'ERROR'))
);

CREATE INDEX IF NOT EXISTS idx_nfse_pdf_jobs_tenant_invoice
  ON nfse_pdf_jobs (tenant_id, invoice_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_nfse_pdf_jobs_status_requested
  ON nfse_pdf_jobs (status, requested_at ASC);

CREATE TABLE IF NOT EXISTS nfse_certificate_unlock_sessions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  certificate_id UUID NOT NULL REFERENCES fiscal_certificates(id) ON DELETE RESTRICT,
  unlock_token_id VARCHAR(120) NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_nfse_unlock_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_nfse_unlock_token
  ON nfse_certificate_unlock_sessions (unlock_token_id);

CREATE INDEX IF NOT EXISTS idx_nfse_unlock_tenant_user_status
  ON nfse_certificate_unlock_sessions (tenant_id, user_id, status, expires_at DESC);

-- <<< END V21__nfse_pdf_jobs_and_unlock.sql

-- >>> BEGIN V23__chat_menu_permissions.sql

-- Menu Chat: liberar acesso de menu para OWNER e PROFESSIONAL
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/chat', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/chat', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = EXCLUDED.updated_at;

-- <<< END V23__chat_menu_permissions.sql

-- >>> BEGIN V24__appointment_booking_funnel_mv.sql

CREATE TABLE IF NOT EXISTS appointment_booking_funnel_events (
  id UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  session_id UUID NOT NULL,
  stage VARCHAR(40) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_appointment_booking_funnel_events_stage CHECK (
    stage IN (
      'SERVICE_SELECTION',
      'PROFESSIONAL_SELECTION',
      'TIME_SELECTION',
      'FINAL_REVIEW',
      'COMPLETED'
    )
  )
);

CREATE INDEX IF NOT EXISTS idx_booking_funnel_events_tenant_occurred
  ON appointment_booking_funnel_events (tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_booking_funnel_events_tenant_session_occurred
  ON appointment_booking_funnel_events (tenant_id, session_id, occurred_at DESC);

DROP MATERIALIZED VIEW IF EXISTS mv_appointment_booking_abandon_daily_stage;

CREATE MATERIALIZED VIEW mv_appointment_booking_abandon_daily_stage AS
WITH session_last_stage AS (
  SELECT
    e.tenant_id,
    (e.occurred_at AT TIME ZONE 'America/Sao_Paulo')::date AS metric_date,
    e.session_id,
    e.stage,
    ROW_NUMBER() OVER (
      PARTITION BY e.tenant_id,
                   (e.occurred_at AT TIME ZONE 'America/Sao_Paulo')::date,
                   e.session_id
      ORDER BY e.occurred_at DESC, e.created_at DESC, e.id DESC
    ) AS rn
  FROM appointment_booking_funnel_events e
)
SELECT
  s.tenant_id,
  s.metric_date,
  s.stage,
  COUNT(*)::int AS sessions_count
FROM session_last_stage s
WHERE s.rn = 1
  AND s.stage <> 'COMPLETED'
GROUP BY s.tenant_id, s.metric_date, s.stage;

CREATE UNIQUE INDEX uq_mv_appointment_booking_abandon_daily_stage_tenant_date_stage
  ON mv_appointment_booking_abandon_daily_stage (tenant_id, metric_date, stage);

CREATE INDEX idx_mv_appointment_booking_abandon_daily_stage_metric_date
  ON mv_appointment_booking_abandon_daily_stage (metric_date);

CREATE INDEX idx_mv_appointment_booking_abandon_daily_stage_tenant_date
  ON mv_appointment_booking_abandon_daily_stage (tenant_id, metric_date DESC);

REFRESH MATERIALIZED VIEW mv_appointment_booking_abandon_daily_stage;

-- <<< END V24__appointment_booking_funnel_mv.sql

-- >>> BEGIN V25__chat_core_tables.sql

CREATE TABLE IF NOT EXISTS chat_conversations (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
  channel VARCHAR(20) NOT NULL,
  external_contact_id VARCHAR(120),
  appointment_marker VARCHAR(30) NOT NULL,
  last_message_at TIMESTAMPTZ,
  last_message_preview VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_chat_conversations_channel CHECK (channel IN ('WHATSAPP')),
  CONSTRAINT ck_chat_conversations_marker CHECK (appointment_marker IN (
    'NAO_INICIADO',
    'EM_ANDAMENTO',
    'PAUSADO',
    'CONCLUIDO',
    'NAO_COMPARECEU',
    'CANCELADO'
  ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_conversations_tenant_client_channel
  ON chat_conversations (tenant_id, client_id, channel);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_tenant_client
  ON chat_conversations (tenant_id, client_id);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_tenant_last_message
  ON chat_conversations (tenant_id, last_message_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_tenant_marker
  ON chat_conversations (tenant_id, appointment_marker);

CREATE TABLE IF NOT EXISTS chat_messages (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
  client_id UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
  direction VARCHAR(10) NOT NULL,
  content TEXT,
  status VARCHAR(20) NOT NULL,
  provider_message_id VARCHAR(120),
  provider_error_code VARCHAR(60),
  provider_error_message VARCHAR(255),
  sent_at TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ,
  read_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_chat_messages_direction CHECK (direction IN ('OUTBOUND', 'INBOUND')),
  CONSTRAINT ck_chat_messages_status CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_tenant_conversation_created
  ON chat_messages (tenant_id, conversation_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_messages_tenant_provider_message
  ON chat_messages (tenant_id, provider_message_id)
  WHERE provider_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chat_messages_tenant_status
  ON chat_messages (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_chat_messages_expires_at
  ON chat_messages (expires_at);

-- <<< END V25__chat_core_tables.sql

-- >>> BEGIN V26__audit_retention_purge_support.sql

-- Permite expurgo controlado apenas para audit_events via scheduler.
-- A protecao append-only permanece para UPDATE e para todas as outras tabelas.

CREATE OR REPLACE FUNCTION deny_append_only_mutation()
RETURNS trigger AS $$
BEGIN
  IF TG_TABLE_NAME = 'audit_events'
     AND TG_OP = 'DELETE'
     AND current_setting('app.audit_retention_purge', true) = 'on' THEN
    RETURN OLD;
  END IF;

  RAISE EXCEPTION 'append-only table: mutation blocked';
END;
$$ LANGUAGE plpgsql;

-- <<< END V26__audit_retention_purge_support.sql

-- >>> BEGIN V27__notifications_permissions.sql

-- Permissoes de notificacao:
-- - PROFESSIONAL pode visualizar notificacoes
-- - OWNER pode visualizar e gerenciar (exclusao)

INSERT INTO permissions (id, code, description)
VALUES
  (public.uuid_generate_v4(), 'notification:read', 'Permite visualizar notificacoes'),
  (public.uuid_generate_v4(), 'notification:writer', 'Permite remover notificacoes')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT ro.id, p.id
FROM roles ro
JOIN permissions p ON p.code IN ('notification:read', 'notification:writer')
WHERE ro.name = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT ro.id, p.id
FROM roles ro
JOIN permissions p ON p.code = 'notification:read'
WHERE ro.name = 'PROFESSIONAL'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Menu de notificacoes habilitado para OWNER e PROFESSIONAL
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/notificacoes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/notificacoes', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- <<< END V27__notifications_permissions.sql

-- >>> BEGIN V28__notifications_viewed.sql

ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS viewed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_viewed_created
  ON notifications (tenant_id, viewed_at, created_at DESC, id DESC);

-- <<< END V28__notifications_viewed.sql

-- >>> BEGIN V29__checkout_tenant_not_null.sql

-- Hardening checkout: impedir registros sem tenant no fluxo de licenciamento.
DELETE FROM orders WHERE tenant_id IS NULL;
DELETE FROM checkout_intents WHERE tenant_id IS NULL;

ALTER TABLE checkout_intents
  ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE orders
  ALTER COLUMN tenant_id SET NOT NULL;

-- <<< END V29__checkout_tenant_not_null.sql

-- >>> BEGIN V30__audit_retention_config.sql

CREATE TABLE IF NOT EXISTS audit_retention_config (
    id SMALLINT PRIMARY KEY,
    retention_period_days INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120),
    CONSTRAINT ck_audit_retention_config_singleton CHECK (id = 1),
    CONSTRAINT ck_audit_retention_days_positive CHECK (retention_period_days > 0)
);

INSERT INTO audit_retention_config (id, retention_period_days, updated_by)
VALUES (1, 365, 'migration_v30')
ON CONFLICT (id) DO NOTHING;

-- <<< END V30__audit_retention_config.sql

-- >>> BEGIN V31__specialties_description.sql

ALTER TABLE specialties
  ADD COLUMN IF NOT EXISTS description VARCHAR(500);

-- <<< END V31__specialties_description.sql

-- >>> BEGIN V32__system_admin_menu_permission.sql

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES ('OWNER', '/configuracoes/admin-sistema', TRUE, now(), now())
ON CONFLICT (role, route) DO UPDATE SET
  is_active = EXCLUDED.is_active,
  updated_at = now();

-- <<< END V32__system_admin_menu_permission.sql

-- >>> BEGIN V34__admin_route_only_for_admin_role.sql

UPDATE menu_role_permissions
SET is_active = FALSE,
    updated_at = NOW()
WHERE role = 'OWNER'
  AND route = '/configuracoes/admin-sistema';

-- <<< END V34__admin_route_only_for_admin_role.sql

-- >>> BEGIN V35__tenant_document.sql

ALTER TABLE tenants
ADD COLUMN IF NOT EXISTS document VARCHAR(20);


-- <<< END V35__tenant_document.sql

-- >>> BEGIN V36__menu_stock_settings_route.sql

-- Inclui rota de configuracao de estoque no catalogo administrativo de menus.
-- Necessario para permitir habilitar/desabilitar no Admin Sistema.

INSERT INTO item_menu (id, route, label, is_active, created_at, updated_at)
VALUES (public.uuid_generate_v4(), '/configuracoes/estoque', 'Configuracoes de estoque', TRUE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    updated_at = NOW();

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES ('OWNER', '/configuracoes/estoque', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- <<< END V36__menu_stock_settings_route.sql

-- >>> BEGIN V37__suggestions_module.sql

CREATE TABLE IF NOT EXISTS feedback_suggestions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  user_name VARCHAR(160) NOT NULL,
  user_role VARCHAR(40) NOT NULL,
  category VARCHAR(30) NOT NULL DEFAULT 'MELHORIA',
  title VARCHAR(160) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  source_page VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_tenant_created
  ON feedback_suggestions (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_status_created
  ON feedback_suggestions (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_category_created
  ON feedback_suggestions (category, created_at DESC);

INSERT INTO item_menu (id, route, label, is_active, created_at, updated_at)
VALUES (public.uuid_generate_v4(), '/sugestoes', 'Sugestoes', TRUE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
SET label = EXCLUDED.label,
    is_active = TRUE,
    updated_at = NOW();

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES
  ('OWNER', '/sugestoes', TRUE, NOW(), NOW()),
  ('PROFESSIONAL', '/sugestoes', TRUE, NOW(), NOW()),
  ('ADMIN', '/sugestoes', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();

-- <<< END V37__suggestions_module.sql

-- >>> BEGIN V38__suggestions_admin_response.sql

ALTER TABLE feedback_suggestions
  ADD COLUMN IF NOT EXISTS admin_response TEXT,
  ADD COLUMN IF NOT EXISTS responded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS responded_by_user_name VARCHAR(160),
  ADD COLUMN IF NOT EXISTS responded_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_status_updated
  ON feedback_suggestions (status, updated_at DESC);

-- <<< END V38__suggestions_admin_response.sql

-- >>> BEGIN V39__suggestions_schema_unified.sql

CREATE TABLE IF NOT EXISTS feedback_suggestions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  user_name VARCHAR(160) NOT NULL,
  user_role VARCHAR(40) NOT NULL,
  category VARCHAR(30) NOT NULL DEFAULT 'MELHORIA',
  title VARCHAR(160) NOT NULL,
  message TEXT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  admin_response TEXT,
  responded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  responded_by_user_name VARCHAR(160),
  responded_at TIMESTAMPTZ,
  closed_at TIMESTAMPTZ,
  source_page VARCHAR(160),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE feedback_suggestions
  ADD COLUMN IF NOT EXISTS category VARCHAR(30) NOT NULL DEFAULT 'MELHORIA',
  ADD COLUMN IF NOT EXISTS admin_response TEXT,
  ADD COLUMN IF NOT EXISTS responded_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS responded_by_user_name VARCHAR(160),
  ADD COLUMN IF NOT EXISTS responded_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_tenant_created
  ON feedback_suggestions (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_status_created
  ON feedback_suggestions (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_category_created
  ON feedback_suggestions (category, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_suggestions_status_updated
  ON feedback_suggestions (status, updated_at DESC);

-- <<< END V39__suggestions_schema_unified.sql

-- >>> BEGIN V41__chat_manual_mode_unified.sql

ALTER TABLE chat_conversations
  ADD COLUMN IF NOT EXISTS manual_mode_until TIMESTAMP,
  ADD COLUMN IF NOT EXISTS manual_mode_by_user_id UUID,
  ADD COLUMN IF NOT EXISTS manual_mode_reason VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_chat_conversations_manual_mode_until
  ON chat_conversations (manual_mode_until);

-- <<< END V41__chat_manual_mode_unified.sql

-- >>> BEGIN V42__commission_schema_unified.sql

CREATE TABLE IF NOT EXISTS commission_rule_sets (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  scope_type VARCHAR(20) NOT NULL,
  professional_id UUID REFERENCES professionals(id) ON DELETE CASCADE,
  name VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_commission_rule_sets_scope CHECK (scope_type IN ('GLOBAL', 'PROFESSIONAL')),
  CONSTRAINT ck_commission_rule_sets_professional_scope CHECK (
    (scope_type = 'GLOBAL' AND professional_id IS NULL)
    OR
    (scope_type = 'PROFESSIONAL' AND professional_id IS NOT NULL)
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_rule_sets_active_scope
  ON commission_rule_sets (
    tenant_id,
    scope_type,
    COALESCE(CAST(professional_id AS TEXT), '__GLOBAL__')
  )
  WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_commission_rule_sets_tenant_scope_active
  ON commission_rule_sets (tenant_id, scope_type, active);

CREATE INDEX IF NOT EXISTS idx_commission_rule_sets_tenant_professional
  ON commission_rule_sets (tenant_id, professional_id);

CREATE TABLE IF NOT EXISTS commission_rules (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  rule_set_id UUID NOT NULL REFERENCES commission_rule_sets(id) ON DELETE CASCADE,
  target_type VARCHAR(30) NOT NULL,
  target_id UUID,
  target_code VARCHAR(160),
  percent_value DOUBLE PRECISION NOT NULL DEFAULT 0,
  fixed_amount_cents BIGINT NOT NULL DEFAULT 0,
  percent_base_type VARCHAR(20) NOT NULL,
  refund_policy VARCHAR(30) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_commission_rules_target_type CHECK (
    target_type IN ('GENERAL', 'SERVICE', 'SERVICE_CATEGORY', 'PRODUCT', 'PRODUCT_CATEGORY')
  ),
  CONSTRAINT ck_commission_rules_percent_base_type CHECK (
    percent_base_type IN ('GROSS', 'NET_OF_DISCOUNT')
  ),
  CONSTRAINT ck_commission_rules_refund_policy CHECK (
    refund_policy IN ('KEEP_COMMISSION', 'REVERSE_COMMISSION')
  ),
  CONSTRAINT ck_commission_rules_amounts CHECK (
    percent_value >= 0
    AND percent_value <= 100
    AND fixed_amount_cents >= 0
    AND NOT (percent_value = 0 AND fixed_amount_cents = 0)
  ),
  CONSTRAINT ck_commission_rules_target_id CHECK (
    (
      target_type = 'GENERAL'
      AND target_id IS NULL
      AND target_code IS NULL
    )
    OR
    (
      target_type IN ('SERVICE', 'PRODUCT')
      AND target_id IS NOT NULL
      AND target_code IS NULL
    )
    OR
    (
      target_type IN ('SERVICE_CATEGORY', 'PRODUCT_CATEGORY')
      AND target_id IS NULL
      AND target_code IS NOT NULL
    )
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_rules_active_target
  ON commission_rules (
    rule_set_id,
    target_type,
    COALESCE(CAST(target_id AS TEXT), ''),
    COALESCE(target_code, '')
  )
  WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_commission_rules_tenant_rule_set_active
  ON commission_rules (tenant_id, rule_set_id, active);

CREATE TABLE IF NOT EXISTS commission_cycles (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  closed_at TIMESTAMPTZ,
  closed_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  paid_at TIMESTAMPTZ,
  paid_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
  total_amount_cents BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_commission_cycles_status CHECK (status IN ('OPEN', 'CLOSED', 'PAID')),
  CONSTRAINT ck_commission_cycles_period CHECK (period_end >= period_start)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_commission_cycles_tenant_period
  ON commission_cycles (tenant_id, period_start, period_end);

CREATE INDEX IF NOT EXISTS idx_commission_cycles_tenant_status
  ON commission_cycles (tenant_id, status, period_start DESC);

CREATE TABLE IF NOT EXISTS commission_entries (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  professional_id UUID NOT NULL REFERENCES professionals(id) ON DELETE RESTRICT,
  cycle_id UUID REFERENCES commission_cycles(id) ON DELETE SET NULL,
  origin_type VARCHAR(30) NOT NULL,
  origin_id UUID,
  origin_reference VARCHAR(160),
  rule_set_id UUID REFERENCES commission_rule_sets(id) ON DELETE SET NULL,
  rule_id UUID REFERENCES commission_rules(id) ON DELETE SET NULL,
  period_key VARCHAR(7) NOT NULL,
  base_amount_cents BIGINT NOT NULL DEFAULT 0,
  percent_value DOUBLE PRECISION NOT NULL DEFAULT 0,
  percent_amount_cents BIGINT NOT NULL DEFAULT 0,
  fixed_amount_cents BIGINT NOT NULL DEFAULT 0,
  total_amount_cents BIGINT NOT NULL DEFAULT 0,
  entry_status VARCHAR(20) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  reversed_at TIMESTAMPTZ,
  reversal_entry_id UUID REFERENCES commission_entries(id) ON DELETE SET NULL,
  CONSTRAINT ck_commission_entries_origin_type CHECK (
    origin_type IN ('SERVICE', 'PRODUCT', 'MANUAL_ADJUSTMENT')
  ),
  CONSTRAINT ck_commission_entries_status CHECK (
    entry_status IN ('OPEN', 'REVERSED', 'PAID')
  ),
  CONSTRAINT ck_commission_entries_percent_value CHECK (
    percent_value >= 0 AND percent_value <= 100
  ),
  CONSTRAINT ck_commission_entries_period_key CHECK (
    period_key ~ '^[0-9]{4}-[0-9]{2}$'
  ),
  CONSTRAINT ck_commission_entries_manual_origin CHECK (
    (origin_type = 'MANUAL_ADJUSTMENT')
    OR
    (origin_type <> 'MANUAL_ADJUSTMENT' AND origin_id IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_commission_entries_tenant_professional_period
  ON commission_entries (tenant_id, professional_id, period_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_commission_entries_tenant_cycle
  ON commission_entries (tenant_id, cycle_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_commission_entries_tenant_origin
  ON commission_entries (tenant_id, origin_type, origin_id);

CREATE INDEX IF NOT EXISTS idx_commission_entries_tenant_status
  ON commission_entries (tenant_id, entry_status, created_at DESC);

-- <<< END V42__commission_schema_unified.sql

-- >>> BEGIN V43__finance_product_commissions_unified.sql

ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS professional_id UUID REFERENCES professionals(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS stock_item_id UUID REFERENCES itens_estoque(id) ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS product_category VARCHAR(160);

CREATE INDEX IF NOT EXISTS idx_transactions_tenant_professional
  ON transactions (tenant_id, professional_id, date DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_tenant_stock_item
  ON transactions (tenant_id, stock_item_id, date DESC);

-- <<< END V43__finance_product_commissions_unified.sql

-- >>> BEGIN V44__menu_catalog_admin_unified.sql

ALTER TABLE item_menu
  ADD COLUMN IF NOT EXISTS parent_item_menu_id UUID REFERENCES item_menu(id) ON UPDATE CASCADE ON DELETE SET NULL,
  ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS icon_key VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_item_menu_parent_order
  ON item_menu (parent_item_menu_id, display_order, label);

-- <<< END V44__menu_catalog_admin_unified.sql
