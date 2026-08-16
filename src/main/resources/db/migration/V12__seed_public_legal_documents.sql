-- Seed dos documentos legais publicos consumidos por /api/v1/public/legal/*.
-- Rotas publicas, portanto nao entram no catalogo administrativo de menus.

WITH terms_upsert AS (
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
    'TERMS_OF_USE',
    '2026.03',
    'Termos de Uso - Azzo Agenda Pro',
    $terms$# Termos de Uso - Azzo Agenda Pro

Versao: 2026.03  
Data de vigencia: 2026-02-27  
Status: minuta para validacao juridica final

## 1. Partes
Este instrumento regula o uso da plataforma Azzo Agenda Pro ("Plataforma"), disponibilizada ao cliente contratante ("Contratante"), pessoa fisica ou juridica que realiza cadastro e utiliza os servicos.

## 2. Objeto
Os presentes Termos disciplinam o acesso e uso da Plataforma, incluindo funcionalidades de agenda, cadastro, autenticacao, financeiro, fiscal, relatorios, integracoes e trilha de auditoria.

## 3. Aceite e vinculacao
Ao criar conta, acessar ou utilizar a Plataforma, o usuario declara ciencia e concordancia com estes Termos e com a Politica de Privacidade vigente.

Registro probatorio de aceite:
- versao dos documentos aceitos;
- data/hora do aceite;
- `request_id` e `ip_address` quando aplicavel;
- hash probatorio de integridade.

## 4. Cadastro e responsabilidades do usuario
- Fornecer dados verdadeiros, exatos e atualizados.
- Manter credenciais de acesso sob sigilo.
- Comunicar imediatamente suspeita de uso indevido da conta.
- Responder pelas acoes executadas por suas credenciais, salvo comprovacao de uso indevido sem culpa.

## 5. Regras de uso
E vedado:
- utilizar a Plataforma para atividade ilicita, fraudulenta ou em desconformidade legal;
- tentar burlar seguranca, explorar vulnerabilidades ou degradar o servico;
- tratar dados pessoais sem base legal adequada;
- compartilhar acesso com terceiros nao autorizados.

## 6. Auditoria, seguranca e registros
A Plataforma pode registrar eventos tecnicos e operacionais para seguranca, compliance e exercicio regular de direitos, incluindo, quando aplicavel:
- usuario, modulo, acao, status e data/hora;
- `request_id`, `ip_address`, `user_agent`;
- metadados necessarios para rastreabilidade.

Esses registros seguem controles de integridade e politica de retencao.

Prazo padrao de retencao para trilha de auditoria: 24 meses, ressalvadas exigencias legais ou necessidade de preservacao de evidencia.

## 7. Integracoes de terceiros
A Plataforma pode operar com provedores externos (ex.: fiscal, pagamento, notificacoes). Eventuais indisponibilidades, erros ou limitacoes desses terceiros podem afetar funcionalidades dependentes, sem descaracterizar a prestacao principal.

## 8. Propriedade intelectual
Todos os direitos sobre software, codigo, marcas, layout, documentacao e materiais da Plataforma pertencem ao titular da Azzo Agenda Pro, sem cessao de titularidade ao Contratante.

## 9. Disponibilidade e manutencao
A Plataforma sera disponibilizada em regime de melhor esforco tecnico, sujeita a manutencoes, atualizacoes e indisponibilidades temporarias justificadas.

## 10. Suspensao e encerramento
O acesso podera ser suspenso ou encerrado em caso de:
- descumprimento destes Termos;
- exigencia legal/regulatoria;
- risco relevante de seguranca.

Em caso de encerramento contratual, aplicam-se as regras de retencao e descarte previstas na Politica de Privacidade e em obrigacoes legais.

## 11. Limitacao de responsabilidade
Na extensao permitida por lei, cada parte respondera pelos danos que der causa. A Plataforma nao responde por:
- falhas causadas por fatores externos fora de controle razoavel;
- atos de terceiros integrados;
- uso indevido da conta por negligencia do usuario.

Nada neste item limita hipoteses legais de responsabilidade obrigatoria.

## 12. Alteracoes destes Termos
Estes Termos podem ser atualizados periodicamente. Novas versoes serao publicadas com identificacao de versao e data de vigencia, mantendo historico para fins probatorios.

## 13. Lei aplicavel e foro
Aplica-se a legislacao da Republica Federativa do Brasil. O foro competente sera definido conforme instrumento contratual comercial, respeitadas disposicoes legais de competencia.

## 14. Contato
Canal oficial de atendimento juridico/contratual: [privacidade@azzoholding.com.br](mailto:privacidade@azzoholding.com.br)  
Canal de privacidade e dados pessoais: [privacidade@azzoholding.com.br](mailto:privacidade@azzoholding.com.br)

## 15. Observacao importante
Este documento e uma minuta padrao operacional e deve ser revisado por assessoria juridica antes de publicacao definitiva.
$terms$,
    'e57f7bd9fadf701ee94842519667914fa9ac12461ba059aede5beec926fc7394',
    NOW(),
    NULL
  )
  ON CONFLICT (document_type, version) DO NOTHING
  RETURNING id
),
terms_version AS (
  SELECT id
  FROM terms_upsert
  UNION ALL
  SELECT tv.id
  FROM terms_versions tv
  WHERE tv.document_type = 'TERMS_OF_USE'
    AND tv.version = '2026.03'
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
  '{"source":"seed-unified","document":"TERMS_OF_USE"}',
  NOW(),
  NULL
FROM terms_version tv
WHERE NOT EXISTS (
  SELECT 1
  FROM terms_lifecycle_events tle
  WHERE tle.terms_version_id = tv.id
    AND tle.event_type = 'PUBLISHED'
);

WITH privacy_upsert AS (
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
    '2026.03',
    'Politica de Privacidade - Azzo Agenda Pro',
    $privacy$# Politica de Privacidade - Azzo Agenda Pro

Versao: 2026.03  
Data de vigencia: 2026-02-27  
Status: minuta para validacao juridica final

## 1. Objetivo
Esta Politica descreve como a Azzo Agenda Pro trata dados pessoais no contexto da Plataforma, em conformidade com a Lei Geral de Protecao de Dados Pessoais (Lei n. 13.709/2018 - LGPD) e demais normas aplicaveis.

## 2. Quem e o agente de tratamento
- Controlador: Azzo Agenda Pro Azzo Holding Ltda (CNPJ: 52.483.646/0001-09).
- Contato privacidade/DPO: [privacidade@azzoholding.com.br](mailto:privacidade@azzoholding.com.br).
- Atendimento de direitos do titular: ate 15 dias corridos para resposta inicial e ate 30 dias corridos para conclusao, salvo complexidade justificada.

## 3. Quais dados podem ser tratados
Dependendo do uso da Plataforma, podem ser tratados:
- dados cadastrais: nome, email, telefone, dados do estabelecimento;
- dados de conta e autenticacao: identificadores de usuario e sessoes;
- dados operacionais: interacoes com modulos, configuracoes e historicos necessarios ao servico;
- dados de seguranca e auditoria: `request_id`, `ip_address`, `user_agent`, data/hora, acao e status;
- dados de integracoes: informacoes necessarias a provedores de pagamento, fiscal e notificacoes.

## 4. Finalidades do tratamento
Os dados sao tratados para:
- executar o contrato e disponibilizar funcionalidades da Plataforma;
- autenticar usuarios e prevenir fraudes;
- cumprir obrigacoes legais e regulatorias;
- registrar trilha de auditoria para seguranca e exercicio regular de direitos;
- atender suporte tecnico e demandas operacionais.

## 5. Bases legais (LGPD)
O tratamento ocorre conforme base legal aplicavel, especialmente:
- execucao de contrato;
- cumprimento de obrigacao legal ou regulatoria;
- exercicio regular de direitos;
- legitimo interesse, com avaliacao de necessidade e proporcionalidade;
- consentimento, quando exigido para finalidade especifica.

## 6. Compartilhamento de dados
Dados podem ser compartilhados:
- com operadores e fornecedores estritamente necessarios a operacao;
- com autoridades publicas, quando houver obrigacao legal;
- em operacoes societarias, observado o dever de confidencialidade e continuidade de protecao.

Nao comercializamos dados pessoais.

### 6.1 Operadores/suboperadores previstos no ambiente
- Provedor de pagamentos: Asaas (quando habilitado).
- Provedor fiscal: provider configurado no modulo fiscal (mock/sandbox/producao, conforme contrato).
- Provedor de email transacional: Brevo SMTP (quando habilitado).
- Infraestrutura de banco e hospedagem: fornecedor de nuvem/hosting contratado pelo Controlador.

Observacao:
- A lista final em producao deve refletir os contratos efetivamente ativos e o DPA correspondente de cada operador.

## 7. Retencao e descarte
Os dados sao mantidos pelo prazo necessario para cumprir as finalidades desta Politica, obrigacoes legais e defesa de direitos.

Para eventos de auditoria, aplica-se politica de retencao tecnica e juridica, com rotinas de expurgo e registro de evidencia de remocao quando cabivel.

### 7.1 Prazos objetivos de retencao (padrao operacional)
- Cadastro de conta e dados do estabelecimento: enquanto houver relacao ativa e por ate 5 anos apos encerramento para defesa de direitos.
- Dados de autenticacao e sessao: prazo operacional de seguranca, ate 12 meses, ressalvadas necessidades de investigacao.
- Trilha de auditoria (`audit_events`): 24 meses (padrao), prorrogavel por obrigacao legal ou preservacao de evidencia.
- Eventos de expurgo (`audit_retention_events`): 5 anos.
- Aceite de termos e politica (`terms_acceptances`): 5 anos apos o fim da relacao contratual.
- Registros financeiros/fiscais: conforme exigencia legal/regulatoria aplicavel.

Expurgo:
- O descarte segue rotina controlada e registravel, com evidencia tecnica de execucao.

## 8. Seguranca da informacao
Adotamos medidas tecnicas e administrativas adequadas, incluindo:
- controle de acesso por perfil e principio do menor privilegio;
- trilhas de auditoria e rastreabilidade;
- mascaramento de campos sensiveis;
- monitoramento e resposta a incidentes.

Nenhum sistema e absolutamente invulneravel, mas a Plataforma atua com boas praticas para reduzir riscos.

## 9. Direitos do titular
Nos termos da LGPD, o titular pode solicitar, quando aplicavel:
- confirmacao da existencia de tratamento;
- acesso aos dados;
- correcao de dados incompletos, inexatos ou desatualizados;
- anonimização, bloqueio ou eliminacao;
- portabilidade;
- informacao sobre compartilhamentos;
- revogacao de consentimento (quando essa for a base legal).

As solicitacoes devem ser enviadas ao canal oficial informado nesta Politica.

### 9.1 Fluxo de atendimento ao titular (SLA)
- Confirmacao de recebimento: ate 2 dias uteis.
- Triagem de identidade e escopo: ate 5 dias uteis.
- Resposta inicial: ate 15 dias corridos.
- Conclusao: ate 30 dias corridos, salvo casos complexos com justificativa formal.
- Registro: toda solicitacao gera protocolo interno auditavel.

## 10. Cookies e tecnologias similares
Quando aplicavel, podemos utilizar cookies e tecnologias similares para autenticacao, seguranca, desempenho e experiencia de uso.

## 11. Transferencia internacional
Quando houver transferencia internacional de dados, serao adotadas salvaguardas adequadas conforme legislacao aplicavel.

## 12. Atualizacoes desta Politica
Esta Politica pode ser alterada periodicamente. A versao vigente sera sempre publicada com data de vigencia e identificador de versao.

## 13. Matriz de bases legais
A matriz de base legal por tipo de tratamento deve ser mantida pelo Controlador e revisada periodicamente.
Referencia operacional: `docs/MATRIZ_BASES_LEGAIS_LGPD.md`.

## 14. Contato
Canal de privacidade: [privacidade@azzoholding.com.br](mailto:privacidade@azzoholding.com.br)  
Canal de suporte: [privacidade@azzoholding.com.br](mailto:privacidade@azzoholding.com.br)

## 15. Observacao importante
Esta Politica e uma minuta padrao operacional e deve ser revisada por assessoria juridica antes de publicacao definitiva.
$privacy$,
    'bc8c62a02581dda124e34d59b799ca5ab38af3733689cc9fc806aaad0994de14',
    NOW(),
    NULL
  )
  ON CONFLICT (document_type, version) DO NOTHING
  RETURNING id
),
privacy_version AS (
  SELECT id
  FROM privacy_upsert
  UNION ALL
  SELECT tv.id
  FROM terms_versions tv
  WHERE tv.document_type = 'PRIVACY_POLICY'
    AND tv.version = '2026.03'
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
  '{"source":"seed-unified","document":"PRIVACY_POLICY"}',
  NOW(),
  NULL
FROM privacy_version tv
WHERE NOT EXISTS (
  SELECT 1
  FROM terms_lifecycle_events tle
  WHERE tle.terms_version_id = tv.id
    AND tle.event_type = 'PUBLISHED'
);
