-- V83: Publica a Politica de Privacidade versao 2026.06
-- Versao elaborada com base em analise tecnica completa do sistema (RIPD v1.0).
-- Substitui a minuta generica 2026.03.
-- Mantém a versao anterior intacta no historico — apenas adiciona a nova versao
-- e registra o evento de publicacao + arquivamento da anterior.

-- ─── 1. Insere a nova versao ────────────────────────────────────────────────
WITH privacy_insert AS (
  INSERT INTO terms_versions (
    id,
    document_type,
    version,
    title,
    content,
    content_hash,
    created_at,
    published_by
  )
  VALUES (
    public.uuid_generate_v4(),
    'PRIVACY_POLICY',
    '2026.06',
    'Politica de Privacidade - Azzo Agenda Pro',
    $pp$# Politica de Privacidade - Azzo Agenda Pro

Versao: 2026.06
Data de vigencia: 2026-06-20
Status: aprovada para publicacao - elaborada com base em analise tecnica completa do sistema (RIPD v1.0)

## 1. Quem somos

O azzo-agenda e uma plataforma de software como servico (SaaS) para gestao de saloes de beleza, barbearias e clinicas de estetica. Por meio da plataforma, profissionais e donos de estabelecimentos gerenciam agendamentos, clientes, servicos, financeiro e comunicacao com clientes via WhatsApp.

A plataforma opera em modelo multi-tenant: cada estabelecimento cadastrado ("tenant") e responsavel pelos dados pessoais de seus proprios clientes. O azzo-agenda atua como Operador em relacao a esses dados e como Controlador em relacao aos dados dos profissionais e proprietarios cadastrados diretamente na plataforma.

Controlador: Azzo Holding Ltda (CNPJ: a preencher)
Contato do Encarregado (DPO): lgpd@azzo.app
Atendimento de direitos do titular: ate 15 dias uteis para resposta inicial

## 2. Quais dados coletamos e por que

### 2.1 Dados de profissionais e proprietarios (usuarios diretos do sistema)

- Nome completo: identificacao no sistema e relatorios - base legal: art. 7, V (execucao de contrato)
- E-mail: autenticacao, envio de credenciais e comunicacao - base legal: art. 7, V (execucao de contrato)
- Senha (hash bcrypt, nunca em claro): autenticacao segura - base legal: art. 7, V (execucao de contrato)
- Telefone/WhatsApp: comunicacao e notificacoes operacionais - base legal: art. 7, V (execucao de contrato)
- Endereco do estabelecimento: emissao de notas fiscais e cadastro - base legal: art. 7, V (execucao de contrato)
- CNPJ/CPF: emissao de NFS-e e obrigacoes fiscais - base legal: art. 7, II (obrigacao legal)
- IP de acesso: seguranca e prevencao de acessos nao autorizados - base legal: art. 7, IX (legitimo interesse)
- Logs de auditoria (acao + timestamp): rastreabilidade e seguranca - base legal: art. 7, IX (legitimo interesse)

### 2.2 Dados de clientes dos estabelecimentos

Estes dados sao coletados pelo estabelecimento e processados pelo azzo-agenda em nome do estabelecimento:

- Nome completo: identificacao do cliente nos agendamentos - base legal: art. 7, V (contrato com o salao)
- Telefone/WhatsApp: lembretes, confirmacoes e assistente de IA - base legal: art. 7, V (contrato com o salao)
- E-mail: comunicacao e notificacoes quando fornecido - base legal: art. 7, V (contrato com o salao)
- Data de nascimento: personalizacao de servicos - base legal: art. 7, V (contrato com o salao)
- CPF/CNPJ: emissao de nota fiscal somente quando solicitado - base legal: art. 7, II (obrigacao legal)
- Endereco completo: emissao de nota fiscal somente quando solicitado - base legal: art. 7, II (obrigacao legal)
- Historico de agendamentos e servicos: gestao do relacionamento e relatorios do salao - base legal: art. 7, V
- Notas de atendimento (*): qualidade e continuidade do atendimento - base legal: art. 7, V / art. 11, II, a (dados de saude)
- Historico de conversa com assistente IA: agendamento automatizado por WhatsApp - base legal: art. 7, V

(*) Notas de atendimento sao campos de texto livre. Se o profissional registrar informacoes sobre condicoes de saude do cliente (alergias, condicoes clinicas, reacoes a produtos), essas informacoes sao consideradas dados sensiveis de saude conforme o art. 11 da LGPD. O sistema exibe aviso ao profissional no momento do preenchimento. O estabelecimento e responsavel por obter consentimento especifico do cliente antes de registrar tais informacoes.

## 3. Como usamos seus dados

Utilizamos os dados pessoais coletados exclusivamente para as finalidades informadas na coleta:

- Gestao de agendamentos, servicos e clientes dos estabelecimentos cadastrados
- Envio de lembretes, confirmacoes e comunicacoes operacionais via WhatsApp e e-mail
- Processamento de pagamentos por meio do gateway Asaas
- Emissao de notas fiscais eletronicas (NFS-e) para os municipios
- Operacao do assistente conversacional de IA para agendamento automatizado
- Autenticacao, controle de acesso e seguranca da conta
- Relatorios gerenciais para os proprietarios dos estabelecimentos
- Cumprimento de obrigacoes legais e fiscais aplicaveis

Nao utilizamos dados pessoais para publicidade direcionada, venda a terceiros ou criacao de perfis comportamentais nao relacionados a finalidade do servico.

## 4. Com quem compartilhamos seus dados

Compartilhamos dados pessoais apenas quando necessario para a prestacao do servico:

- Asaas Pagamentos (Brasil): CPF/CNPJ e dados de pagamento para processamento de cobrancas. Salvaguarda: contrato de servico.
- Meta / WhatsApp Business API (EUA/Global): numero de telefone e mensagens de notificacao para comunicacao ao cliente. Salvaguarda: Termos de Servico Meta.
- Groq Inc. (EUA): historico de conversa anonimizado (sem nome real do cliente) para processamento do assistente de IA. Salvaguarda: alias anonimo implantado; DPA em negociacao.
- SEFAZ / Prefeituras municipais (Brasil): CPF/CNPJ, endereco e valor do servico para emissao de notas fiscais. Salvaguarda: obrigacao legal.
- MinIO - armazenamento de arquivos (configuravel): arquivos de importacao e avatares. Salvaguarda: bucket privado com acesso restrito.

Nao vendemos, alugamos ou cedemos dados pessoais a terceiros para fins comerciais.

## 5. Transferencia internacional de dados

### 5.1 Meta / WhatsApp Business API (EUA)

O numero de telefone dos clientes e as mensagens de notificacao transitam pela infraestrutura da Meta Platforms Inc. (EUA). O uso do WhatsApp pelo cliente implica a aceitacao dos Termos de Servico da Meta. Base legal: art. 33, I da LGPD (consentimento do titular ao utilizar o WhatsApp).

### 5.2 Groq Inc. (EUA) - Assistente de IA

Quando o estabelecimento utiliza o assistente conversacional de IA, o historico de conversa e processado pela Groq Inc. (EUA):

- O nome real do cliente NAO e enviado ao Groq. Utilizamos um alias anonimo (CLIENTE_XXXX) gerado a partir de um hash nao reversivel.
- O contexto de agendamento (servicos, horarios) pode ser incluido para que o assistente responda adequadamente.
- Estamos em processo de negociacao de Contrato de Processamento de Dados (DPA) com a Groq Inc.
- Estabelecimentos que nao utilizarem o assistente de IA nao tem dados enviados ao Groq.

Alternativamente, o azzo-agenda suporta o uso do modelo de IA Ollama (processamento local, sem envio de dados ao exterior).

## 6. Por quanto tempo guardamos seus dados

- Dados cadastrais (clientes, profissionais): duracao do contrato + 5 anos (prescricao civil e obrigacoes fiscais)
- Notas fiscais eletronicas (NFS-e, XML): minimo 5 anos (obrigacao fiscal)
- Logs de auditoria e seguranca: 365 dias configuravel (seguranca e rastreabilidade)
- Registros de pagamento e webhooks Asaas: 30 dias (diagnostico; minimizacao de dados - LGPD art. 6, III)
- Historico de mensagens WhatsApp (log): 90 dias (auditoria do assistente; minimizacao de dados - LGPD art. 6, III)
- Historico de conversas (chat IA): 90 a 365 dias configuravel pelo estabelecimento
- Agendamentos e historico de servicos: duracao do contrato do salao

Apos o encerramento do contrato do estabelecimento, os dados pessoais dos clientes sao mantidos pelo prazo minimo legal e entao eliminados ou anonimizados de forma irreversivel.

## 7. Como protegemos seus dados

- Autenticacao com JWT em cookie HttpOnly: token de sessao inacessivel ao JavaScript, impedindo roubo via XSS
- Segundo fator de autenticacao (MFA/TOTP): protecao adicional contra credenciais comprometidas
- Criptografia de campos sensiveis (AES): tokens de integracao, segredos de MFA e certificados digitais cifrados em banco
- Controle de acesso por papel (RBAC): OWNER e PROFESSIONAL com permissoes distintas; isolamento entre tenants
- Limitacao de requisicoes (rate limiting): protecao contra ataques de forca bruta
- Auditoria com cadeia de hashes: todas as acoes relevantes registradas com hash de integridade SHA-256
- Comunicacao cifrada (HTTPS/TLS): todos os dados em transito sao cifrados
- Validacao de seguranca na inicializacao: o sistema nao inicia em producao sem as chaves de seguranca configuradas
- Pseudonimizacao no assistente de IA: nome do cliente substituido por alias anonimo antes do envio ao provedor externo
- Purga automatica de dados com TTL expirado: job diario remove dados de webhook (30d) e WhatsApp (90d) fora do prazo

Em caso de incidente de seguranca que afete dados pessoais, notificaremos a ANPD no prazo de 72 horas e comunicaremos os titulares afetados conforme o art. 48 da LGPD.

## 8. Seus direitos como titular de dados

Conforme o art. 18 da LGPD, voce tem os seguintes direitos:

- Confirmacao e acesso: saber se tratamos seus dados e acessar uma copia. Prazo: 15 dias uteis.
- Correcao: corrigir dados incompletos, inexatos ou desatualizados.
- Anonimizacao ou bloqueio: bloquear dados desnecessarios ou tratados em excesso.
- Eliminacao: excluir dados tratados com base em consentimento.
- Portabilidade: receber seus dados em formato estruturado e legivel.
- Informacao sobre compartilhamento: saber com quais terceiros compartilhamos seus dados.
- Revogacao do consentimento: retirar consentimento dado anteriormente.
- Peticao a ANPD: reclamar junto a Autoridade Nacional de Protecao de Dados (www.gov.br/anpd).

Para exercer seus direitos, entre em contato pelo canal lgpd@azzo.app. Prazo de resposta: 15 dias uteis.

Para clientes de estabelecimentos, recomendamos contatar primeiro o proprio estabelecimento, que e o responsavel direto pelo tratamento de seus dados.

## 9. Cookies e tecnologias similares

- Cookie de sessao (JWT): manter o usuario autenticado com seguranca (HttpOnly). Essencial.
- sessionStorage (auth_user): dados de sessao do usuario no navegador. Essencial. Limpo ao fechar o navegador.
- localStorage (lembrar login): preencher automaticamente o e-mail no login. Opcional. Pode ser desativado na tela de login.

Nao utilizamos cookies de rastreamento, publicidade ou analise comportamental de terceiros.

## 10. Criancas e adolescentes

A plataforma destina-se exclusivamente a profissionais e estabelecimentos prestadores de servicos. Nao coletamos intencionalmente dados pessoais de criancas (menores de 12 anos) sem o consentimento de seus responsaveis legais. Para clientes menores de idade dos estabelecimentos, o proprio salao e responsavel por observar o art. 14 da LGPD.

## 11. Inteligencia Artificial e decisoes automatizadas

O azzo-agenda utiliza IA no assistente conversacional de WhatsApp:

- O assistente e um sistema de IA automatizado, nao um ser humano.
- As respostas sao geradas com base em regras de negocio do estabelecimento e em um modelo de linguagem externo (Groq ou Ollama).
- Nenhuma decisao com efeitos juridicos significativos e tomada exclusivamente pelo assistente.
- O titular pode solicitar revisao humana de qualquer decisao do assistente via canal de atendimento do estabelecimento.

Os estabelecimentos devem informar seus clientes, na primeira interacao, que estao conversando com um sistema de IA.

## 12. Alteracoes nesta politica

Esta Politica pode ser atualizada periodicamente. Em caso de alteracoes relevantes, notificaremos os usuarios via e-mail cadastrado com antecedencia minima de 15 dias e com aviso no painel da plataforma.

Historico de versoes:
- 2026.03: versao inicial generica (minuta para validacao juridica)
- 2026.06: versao completa elaborada com base em analise tecnica do sistema (RIPD v1.0)

## 13. Contato e Encarregado de Dados (DPO)

E-mail do DPO: lgpd@azzo.app
Formulario de solicitacao LGPD: disponivel na plataforma - menu Conta > Privacidade
Prazo de resposta: 15 dias uteis
Autoridade Nacional de Protecao de Dados (ANPD): www.gov.br/anpd

Esta politica entra em vigor em 2026-06-20 e substitui a versao 2026.03.
$pp$,
    '334e0de83d7fea85896c0f8e2b9a162c888ba11da7273895e719e8fddebaab6e',
    NOW(),
    NULL
  )
  ON CONFLICT (document_type, version) DO NOTHING
  RETURNING id
),
privacy_version AS (
  SELECT id FROM privacy_insert
  UNION ALL
  SELECT tv.id
  FROM terms_versions tv
  WHERE tv.document_type = 'PRIVACY_POLICY'
    AND tv.version = '2026.06'
  LIMIT 1
)
INSERT INTO terms_lifecycle_events (
  id,
  terms_version_id,
  event_type,
  event_metadata_json,
  created_at,
  created_by
)
SELECT
  public.uuid_generate_v4(),
  tv.id,
  'PUBLISHED',
  '{"source":"migration-v83","document":"PRIVACY_POLICY","ripd_version":"1.0","replaces":"2026.03"}',
  NOW(),
  NULL
FROM privacy_version tv
WHERE NOT EXISTS (
  SELECT 1
  FROM terms_lifecycle_events tle
  WHERE tle.terms_version_id = tv.id
    AND tle.event_type = 'PUBLISHED'
);

-- ─── 2. Arquiva a versao anterior (2026.03) ─────────────────────────────────
-- Registra evento de ARCHIVED na versao antiga para indicar que foi superada.
-- A versao 2026.03 permanece no historico para fins probatorios de aceites
-- que porventura tenham sido coletados enquanto estava vigente.
INSERT INTO terms_lifecycle_events (
  id,
  terms_version_id,
  event_type,
  event_metadata_json,
  created_at,
  created_by
)
SELECT
  public.uuid_generate_v4(),
  tv.id,
  'ARCHIVED',
  '{"source":"migration-v83","reason":"superseded_by_2026.06","ripd_version":"1.0"}',
  NOW(),
  NULL
FROM terms_versions tv
WHERE tv.document_type = 'PRIVACY_POLICY'
  AND tv.version = '2026.03'
  AND NOT EXISTS (
    SELECT 1
    FROM terms_lifecycle_events tle
    WHERE tle.terms_version_id = tv.id
      AND tle.event_type = 'ARCHIVED'
  );
