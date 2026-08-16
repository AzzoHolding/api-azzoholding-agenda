-- Fix 1 (race condition webhook WhatsApp): garante a nivel de banco que duas
-- requisicoes concorrentes para o mesmo contato novo (mesmo telefone / mesmo
-- cliente+canal) nao criem registros duplicados de cliente ou de conversa.
-- Isso fecha a janela de corrida que existe entre o SELECT (find) e o INSERT
-- (create) em ChatService#resolveOrCreateClient / #resolveOrCreateConversation,
-- que hoje rodam em transacoes REQUIRES_NEW distintas por mensagem inbound.
--
-- ATENCAO ANTES DE APLICAR EM PRODUCAO:
-- Esta migration falha se ja existirem duplicidades nos dados atuais. Validar
-- previamente com:
--   SELECT tenant_id, phone, COUNT(*) FROM clients
--     WHERE phone IS NOT NULL AND phone <> '' GROUP BY tenant_id, phone HAVING COUNT(*) > 1;
--   SELECT tenant_id, client_id, channel, COUNT(*) FROM chat_conversations
--     GROUP BY tenant_id, client_id, channel HAVING COUNT(*) > 1;
-- Caso existam, resolver manualmente (merge) antes de rodar esta migration.

CREATE UNIQUE INDEX IF NOT EXISTS uq_clients_tenant_phone
  ON clients (tenant_id, phone)
  WHERE phone IS NOT NULL AND phone <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_conversations_tenant_client_channel
  ON chat_conversations (tenant_id, client_id, channel);
