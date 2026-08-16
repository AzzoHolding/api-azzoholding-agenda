-- View auxiliar para monitoramento de dados expirados (LGPD - auditoria de retenção)
-- Requer colunas expires_at criadas em V79 e V80
CREATE OR REPLACE VIEW v_lgpd_expired_data AS
SELECT
  'webhook_event_logs'    AS tabela,
  COUNT(*)                AS registros_expirados,
  MIN(expires_at)         AS mais_antigo
FROM webhook_event_logs
WHERE expires_at < NOW()

UNION ALL

SELECT
  'whatsapp_message_log'  AS tabela,
  COUNT(*)                AS registros_expirados,
  MIN(expires_at)         AS mais_antigo
FROM whatsapp_message_log
WHERE expires_at < NOW();

COMMENT ON VIEW v_lgpd_expired_data
  IS 'LGPD: view de monitoramento de dados expirados aguardando purga. Consultar para auditoria de retencao.';
