-- V146: Publica a Politica de Privacidade 2026.10 e arquiva a 2026.06.
-- Analise LGPD de 2026-10-13: a 2026.06 dizia coisas que o sistema nao fazia (nome do cliente
-- nunca enviado a IA, so Groq/Ollama como provedores, chat guardado de 90 a 365 dias) e omitia
-- o Telegram e prazos de retencao. Esta versao descreve o que o codigo faz hoje.
-- Mesma mecanica da V83: so ADICIONA a versao (a anterior fica no historico, os aceites dela
-- continuam valendo) e registra PUBLISHED/ARCHIVED. Nenhum gatilho ou CHECK novo; o hash e
-- calculado pelo proprio banco sobre o texto gravado.

WITH conteudo AS (
  SELECT $pp$# Politica de Privacidade - Azzo Agenda Pro

Versao: 2026.10
Data de vigencia: 2026-10-14

## 1. Quem somos

O Azzo Agenda Pro e uma plataforma (SaaS) para gestao de saloes de beleza, barbearias e clinicas de estetica: agenda, clientes, servicos, caixa, financeiro, notas fiscais e comunicacao com clientes por WhatsApp e Telegram.

Cada estabelecimento ("salao") e o Controlador dos dados pessoais dos proprios clientes. A Azzo atua como Operadora desses dados, tratando-os em nome e por instrucao do salao. Em relacao aos dados dos proprietarios e profissionais cadastrados diretamente na plataforma, a Azzo e Controladora.

Controlador (dados de usuarios da plataforma): Azzo Holding Ltda
Encarregado (DPO): lgpd@azzo.app

## 2. Quais dados tratamos e por que

### 2.1 Proprietarios e profissionais (usuarios da plataforma)

- Nome, e-mail e telefone: identificacao, acesso e comunicacao - art. 7, V (execucao de contrato)
- Senha (somente o hash, nunca o texto): autenticacao - art. 7, V
- CPF/CNPJ e endereco do estabelecimento: cobranca e notas fiscais - art. 7, II (obrigacao legal)
- IP e registros de acesso e de acoes: seguranca e rastreabilidade - art. 7, IX (legitimo interesse)

### 2.2 Clientes dos saloes (tratados em nome do salao)

- Nome e telefone: agendamento, confirmacao e lembrete do atendimento - art. 7, V (contrato com o salao)
- E-mail, data de nascimento e endereco, quando informados - art. 7, V
- CPF/CNPJ e endereco para nota fiscal, quando solicitada - art. 7, II
- Historico de agendamentos, comandas, pacotes e assinaturas - art. 7, V e II
- Notas de atendimento (*) - art. 7, V; art. 11, II, a quando houver dado de saude
- Mensagens trocadas com o salao pelo WhatsApp ou Telegram, inclusive com o assistente de IA - art. 7, V
- Mensagens de retorno ao salao (reativacao), com opcao de parar a qualquer momento - art. 7, IX (legitimo interesse), com opt-out

(*) Notas de atendimento sao texto livre. Informacao sobre saude do cliente (alergias, condicoes clinicas, reacoes a produtos) e dado sensivel (art. 11). O sistema avisa o profissional no preenchimento, e o salao e responsavel por obter o consentimento especifico do cliente.

## 3. Como usamos os dados

- Gestao de agendamentos, servicos, clientes, caixa e financeiro do salao
- Confirmacoes e lembretes automaticos de atendimento (WhatsApp, Telegram, e-mail)
- Assistente conversacional de IA para agendar pelo WhatsApp ou Telegram, quando o salao o usa
- Cobranca pelo gateway Asaas e emissao de notas fiscais de servico
- Seguranca da conta, controle de acesso e cumprimento de obrigacoes legais

Nao usamos dados pessoais para publicidade direcionada, venda a terceiros ou perfis sem relacao com o servico.

## 4. Com quem compartilhamos

- Meta Platforms (WhatsApp Business) - EUA: telefone e texto das mensagens, para entregar a mensagem
- Telegram (Telegram FZ-LLC) - Emirados Arabes Unidos: identificador do chat e texto das mensagens, quando o salao usa Telegram
- Provedores de modelo de linguagem do assistente de IA - EUA (Groq, OpenAI, OpenRouter, Mistral, Google, conforme configuracao da plataforma) ou processamento local (Ollama): o texto da conversa, quando o salao usa o assistente. O nome cadastrado do cliente e substituido por um codinome antes do envio; o que o proprio cliente escreve na mensagem segue como foi escrito
- Asaas Pagamentos - Brasil: dados de cobranca, quando o salao cobra pela plataforma
- Prefeituras / sistema nacional da NFS-e - Brasil: dados da nota fiscal (obrigacao legal)
- Armazenamento de arquivos (privado, sob controle da Azzo): fotos e planilhas de importacao

Nao vendemos, alugamos ou cedemos dados pessoais.

## 5. Transferencia internacional

Meta (EUA), Telegram (Emirados Arabes Unidos) e os provedores de IA (EUA) ficam fora do Brasil. A transferencia se apoia no art. 33 da LGPD (execucao do contrato com o salao e uso, pelo cliente, do proprio aplicativo de mensagens), com as garantias contratuais de cada provedor. Quem nao quer o envio a provedor de IA pode pedir ao salao para ser atendido por pessoa; o salao pode desligar o assistente.

## 6. Por quanto tempo guardamos

- Mensagens do chat: 2 dias depois do atendimento concluido ou cancelado; 5 dias nos demais casos. Depois disso o texto e apagado
- Estado da conversa com o assistente de IA: 8 horas
- Log de mensagens do WhatsApp: 90 dias
- Registros de envio de reativacao: 1 ano
- Notificacoes (lembretes, avisos): 180 dias
- E-mails de senha e de confirmacao: 30 dias
- Planilhas de importacao de clientes: apagadas ao fim do processamento; linhas com erro, 30 dias
- Webhooks de pagamento: 30 dias
- Trilha de auditoria: 365 dias
- Notas fiscais: prazo legal (no minimo 5 anos)
- Cadastro, agendamentos e historico financeiro: enquanto durar o contrato do salao, e depois pelo prazo legal

Encerrado o contrato do salao, os dados dos clientes dele sao mantidos apenas pelo prazo legal e entao eliminados ou anonimizados, por procedimento da equipe da Azzo.

## 7. Como protegemos

- Sessao em cookie HttpOnly e segundo fator (MFA) opcional
- Texto das mensagens do chat, tokens de integracao, segredos de MFA e certificados cifrados no banco
- Controle de acesso por papel e isolamento entre saloes
- Limite de tentativas contra forca bruta e HTTPS em todo o trafego
- Telefone, e-mail e documento mascarados nos registros (logs) do sistema
- Purga automatica diaria dos dados com prazo vencido (secao 6)

Em incidente de seguranca com risco aos titulares, comunicamos a ANPD e os titulares afetados conforme o art. 48.

## 8. Direitos do titular

Pelo art. 18 da LGPD: confirmacao e acesso, correcao, anonimizacao, bloqueio ou eliminacao, portabilidade, informacao sobre compartilhamento, revogacao do consentimento e peticao a ANPD.

- Clientes de salao: facam o pedido ao salao, que e o controlador. A plataforma permite ao salao exportar todos os dados do cliente em arquivo (acesso e portabilidade), corrigir, anonimizar e excluir, e cada atendimento recebe protocolo.
- Mensagens automaticas: responda PARE (ou STOP, SAIR, REMOVER, NAO QUERO) no WhatsApp ou no Telegram.
- Usuarios da plataforma e qualquer duvida: lgpd@azzo.app. Prazo: confirmacao em ate 2 dias uteis e resposta completa em ate 15 dias.

## 9. Cookies e armazenamento no navegador

- Cookie de sessao (HttpOnly): manter o usuario autenticado. Essencial.
- sessionStorage: dados da sessao aberta (nome, papel, menu). Essencial; some ao fechar o navegador.
- localStorage: preferencias de exibicao (tema claro/escuro, barra lateral, aviso de instalacao do app). Nao guarda dado pessoal.

Nao usamos cookies de rastreamento, publicidade ou analise de terceiros. A pagina publica de agendamento nao usa cookies.

## 10. Criancas e adolescentes

A plataforma e destinada a estabelecimentos. Para clientes menores de idade, o salao deve observar o art. 14. Mensagens de reativacao nao sao enviadas a quem tem menos de 18 anos pela data de nascimento cadastrada.

## 11. Inteligencia artificial

- O assistente de WhatsApp/Telegram e um sistema automatizado, nao uma pessoa, e o salao deve informar isso ao cliente.
- Ele agenda, remarca e responde duvidas; nenhuma decisao com efeito juridico relevante e tomada somente por ele.
- O cliente pode pedir atendimento humano ao salao a qualquer momento.

## 12. Alteracoes

Mudancas relevantes sao avisadas aos usuarios por e-mail e no painel. Os aceites de versoes anteriores continuam registrados.

Historico de versoes:
- 2026.03: minuta inicial
- 2026.06: versao com base na analise tecnica do sistema (RIPD v1.0)
- 2026.10: revisao apos analise LGPD do codigo: provedores de IA e Telegram, codinome do nome do cliente, prazos reais de retencao, exportacao dos dados do cliente e opt-out em qualquer canal

## 13. Contato

Encarregado (DPO): lgpd@azzo.app
ANPD: www.gov.br/anpd

Esta politica vale a partir de 2026-10-14 e substitui a versao 2026.06.
$pp$::text AS c
),
privacy_insert AS (
  INSERT INTO terms_versions (id, document_type, version, title, content, content_hash, created_at, published_by)
  SELECT
    public.uuid_generate_v4(),
    'PRIVACY_POLICY',
    '2026.10',
    'Politica de Privacidade - Azzo Agenda Pro',
    c,
    encode(sha256(convert_to(c, 'UTF8')), 'hex'),
    NOW(),
    NULL
  FROM conteudo
  ON CONFLICT (document_type, version) DO NOTHING
  RETURNING id
),
privacy_version AS (
  SELECT id FROM privacy_insert
  UNION ALL
  SELECT tv.id FROM terms_versions tv
   WHERE tv.document_type = 'PRIVACY_POLICY' AND tv.version = '2026.10'
  LIMIT 1
)
INSERT INTO terms_lifecycle_events (id, terms_version_id, event_type, event_metadata_json, created_at, created_by)
SELECT
  public.uuid_generate_v4(),
  tv.id,
  'PUBLISHED',
  '{"source":"migration-v146","document":"PRIVACY_POLICY","replaces":"2026.06","reason":"analise-lgpd-2026-10-13"}',
  NOW(),
  NULL
FROM privacy_version tv
WHERE NOT EXISTS (
  SELECT 1 FROM terms_lifecycle_events tle
   WHERE tle.terms_version_id = tv.id AND tle.event_type = 'PUBLISHED'
);

INSERT INTO terms_lifecycle_events (id, terms_version_id, event_type, event_metadata_json, created_at, created_by)
SELECT
  public.uuid_generate_v4(),
  tv.id,
  'ARCHIVED',
  '{"source":"migration-v146","reason":"superseded_by_2026.10"}',
  NOW(),
  NULL
FROM terms_versions tv
WHERE tv.document_type = 'PRIVACY_POLICY'
  AND tv.version = '2026.06'
  AND NOT EXISTS (
    SELECT 1 FROM terms_lifecycle_events tle
     WHERE tle.terms_version_id = tv.id AND tle.event_type = 'ARCHIVED'
  );
