-- Texto livre so e entregue dentro de 24h da ultima mensagem do CLIENTE. Confirmacao e lembrete de
-- cliente novo sao sempre primeiro contato: fora da janela a Cloud API aceita, devolve o wamid e
-- DESCARTA. Visto em producao em 2026-09-22 -- tres envios com wamid valido, nenhum entregue.
--
-- Para falar com quem nunca escreveu, e preciso um TEMPLATE aprovado na Meta, e o nome dele e por
-- salao: cada um cadastra o seu, em portugues, com o texto que quiser.
ALTER TABLE tenant_whatsapp_config
  ADD COLUMN IF NOT EXISTS confirmation_template_name     VARCHAR(120),
  ADD COLUMN IF NOT EXISTS confirmation_template_language VARCHAR(12);

COMMENT ON COLUMN tenant_whatsapp_config.confirmation_template_name IS
  'Nome do template aprovado na Meta para a confirmacao de agendamento. Nulo = nao configurado, e a confirmacao nao chega a cliente novo.';
COMMENT ON COLUMN tenant_whatsapp_config.confirmation_template_language IS
  'Idioma do template, como cadastrado na Meta (ex.: pt_BR). Nulo usa o padrao da aplicacao.';
