-- SEG-014 (parte 2): o cadastro embutido passa a guardar quem autorizou o app na Meta.
-- Sem isso, um pedido de exclusao de dados ou de desautorizacao vindo da Meta (que so traz o
-- user_id) nao tinha como chegar ao salao, e o token ficava guardado ate alguem tratar a mao.
ALTER TABLE tenant_whatsapp_config
  ADD COLUMN meta_user_id VARCHAR(64);

CREATE INDEX idx_tenant_whatsapp_config_meta_user_id
  ON tenant_whatsapp_config (meta_user_id)
  WHERE meta_user_id IS NOT NULL;

COMMENT ON COLUMN tenant_whatsapp_config.meta_user_id IS
  'user_id (debug_token) de quem autorizou o app no cadastro embutido; liga os callbacks de exclusao/desautorizacao da Meta ao salao.';
