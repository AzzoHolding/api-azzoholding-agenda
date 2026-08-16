ALTER TABLE chat_conversations
  ADD COLUMN IF NOT EXISTS unread_count INT NOT NULL DEFAULT 0;

UPDATE chat_conversations c
SET unread_count = (
  SELECT COUNT(*)
  FROM chat_messages m
  WHERE m.conversation_id = c.id
    AND m.direction = 'INBOUND'
    AND m.read_at IS NULL
);
