-- O numero conectado pelo Embedded Signup nao envia mensagem enquanto nao for REGISTRADO no Cloud
-- API (POST /{phone-number-id}/register). Ate 2026-09-22 o Azzo nunca fazia essa chamada: o
-- onboarding terminava dizendo "conectado", ligava o WhatsApp, e todo envio morria com
-- "(#133010) Account not registered" -- visto em producao no proprio numero do Azzo.
--
-- O registro exige um PIN de 6 digitos. No modelo de provedor (Azzo administra o numero do
-- cliente), quem define o PIN de um numero novo e o PROVEDOR, e ele precisa ser guardado: sem o
-- PIN o numero nao pode ser re-registrado nem migrado para outro provedor depois. Guardado
-- CRIPTOGRAFADO, como o token de acesso e o verify token do webhook.
ALTER TABLE tenant_whatsapp_config
  ADD COLUMN IF NOT EXISTS whatsapp_registration_pin_enc TEXT,
  ADD COLUMN IF NOT EXISTS whatsapp_registered_at        TIMESTAMPTZ;

COMMENT ON COLUMN tenant_whatsapp_config.whatsapp_registration_pin_enc IS
  'PIN de verificacao em duas etapas usado no registro do numero no Cloud API, criptografado. O dono pode ler na tela: sem ele o numero nao migra de provedor.';
COMMENT ON COLUMN tenant_whatsapp_config.whatsapp_registered_at IS
  'Quando o numero foi registrado no Cloud API com sucesso. Nulo = nunca registrado, e o numero nao envia.';
