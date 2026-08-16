-- A feature de Telegram adicionou ChatChannel.TELEGRAM (enum, adapter de canal,
-- webhook e config por tenant), mas a CHECK de chat_conversations.channel continuou
-- como foi definida no V1 baseline, aceitando apenas 'WHATSAPP':
--
--   CONSTRAINT ck_chat_conversations_channel CHECK (channel IN ('WHATSAPP'))
--
-- Resultado em producao: TODO inbound do Telegram falhava no INSERT da conversa
-- (ChatService#createConversation) com
--   "new row for relation chat_conversations violates check constraint
--    ck_chat_conversations_channel"
-- e a excecao saia do webhook como HTTP 400, fazendo o Telegram acusar
-- "Wrong response from the webhook: 400 Bad Request" e reentregar a mensagem.
--
-- Idempotente: DROP IF EXISTS antes de recriar, para nao depender do estado exato
-- do ambiente. Nao ha backfill a fazer — a constraint so passa a aceitar um valor
-- novo, nenhuma linha existente e invalidada.

ALTER TABLE chat_conversations
  DROP CONSTRAINT IF EXISTS ck_chat_conversations_channel;

ALTER TABLE chat_conversations
  ADD CONSTRAINT ck_chat_conversations_channel CHECK (channel IN ('WHATSAPP', 'TELEGRAM'));
