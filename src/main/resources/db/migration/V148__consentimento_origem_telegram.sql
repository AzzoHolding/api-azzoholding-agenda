-- V148 (LGPD): o "PARE" pelo Telegram passa a ficar no historico de consentimento, como o do WhatsApp.
-- O CHECK de `source` so aceitava WHATSAPP_REPLY/OWNER/SYSTEM/PORTAL, entao o opt-out do Telegram mudava
-- a ficha do cliente mas nao deixava registro. Conferido em producao antes (19/10/2026): a tabela nao
-- tem gatilho, so os CHECKs de action e source. O novo CHECK e um SUPERCONJUNTO do antigo: nenhuma
-- linha existente deixa de valer.

ALTER TABLE reactivation_consent_history
    DROP CONSTRAINT IF EXISTS reactivation_consent_history_source_check;
ALTER TABLE reactivation_consent_history
    ADD CONSTRAINT reactivation_consent_history_source_check
    CHECK (source IN ('WHATSAPP_REPLY', 'OWNER', 'SYSTEM', 'PORTAL', 'TELEGRAM_REPLY'));
