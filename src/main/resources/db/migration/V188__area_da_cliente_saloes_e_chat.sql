-- Area da cliente (20/11/2026): buscar e agendar em qualquer salao da Azzo, e o chat da area como canal novo.
--
-- ATENCAO: as duas tabelas JA EXISTEM EM PRODUCAO. Antes de publicar, conferir no banco de producao (so leitura):
--   - tenant_operational_settings: gatilhos e CHECKs (aqui so entra uma coluna com padrao);
--   - chat_conversations: que o unico CHECK de canal e o ck_chat_conversations_channel (WHATSAPP, TELEGRAM; V113).

-- O dono pode tirar o salao da busca da area da cliente (padrao: aparece).
ALTER TABLE tenant_operational_settings ADD COLUMN IF NOT EXISTS aparecer_na_area_da_cliente BOOLEAN NOT NULL DEFAULT TRUE;

-- Canal novo do chat: a conversa pela area da cliente (mesmo padrao da V113, que liberou o Telegram).
ALTER TABLE chat_conversations DROP CONSTRAINT IF EXISTS ck_chat_conversations_channel;
ALTER TABLE chat_conversations ADD CONSTRAINT ck_chat_conversations_channel
  CHECK (channel IN ('WHATSAPP', 'TELEGRAM', 'AREA_CLIENTE'));
