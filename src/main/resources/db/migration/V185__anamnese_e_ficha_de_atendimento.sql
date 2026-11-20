-- Anamnese e ficha de atendimento por cliente (plano PLANO-ANAMNESE-E-FICHA.md, pedido do dono em 18/11/2026).
-- So tabelas novas, permissoes e itens de menu; nenhuma tabela existente muda de estrutura, exceto a coluna nova de
-- retencao em tenant_operational_settings (aditiva, com DEFAULT).
--
-- Historico que nao se altera: versoes de modelo, versoes de anamnese, versoes de termo, assinaturas e adendos.
-- UPDATE/DELETE nessas tabelas so com set_config('app.lgpd_anonimizacao','on',true), que so a anonimizacao e a
-- retencao fazem (mesmo desenho da redacao da auditoria, V143).

CREATE OR REPLACE FUNCTION negar_alteracao_fora_da_lgpd() RETURNS trigger AS $$
BEGIN
  IF current_setting('app.lgpd_anonimizacao', true) = 'on' THEN
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'Registro historico de % nao pode ser alterado nem apagado', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- ─── Modelos de anamnese ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS modelos_de_anamnese (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  nome VARCHAR(120) NOT NULL,
  segmento VARCHAR(30),
  validade_meses INTEGER CHECK (validade_meses IS NULL OR validade_meses BETWEEN 1 AND 120),
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  versao_atual_id UUID,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_modelos_de_anamnese_tenant ON modelos_de_anamnese (tenant_id, ativo);

-- Perguntas em JSONB: [{id, texto, tipo, obrigatoria, ajuda, opcoes[], condicao{pergunta, valor}, alerta{tipo, quando}}]
CREATE TABLE IF NOT EXISTS versoes_de_modelo_de_anamnese (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  modelo_id UUID NOT NULL REFERENCES modelos_de_anamnese(id),
  numero INTEGER NOT NULL,
  perguntas JSONB NOT NULL,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (modelo_id, numero)
);
CREATE TRIGGER trg_versoes_de_modelo_de_anamnese_historico BEFORE UPDATE OR DELETE ON versoes_de_modelo_de_anamnese
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

-- ─── Anamnese da cliente ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS anamneses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  modelo_id UUID NOT NULL REFERENCES modelos_de_anamnese(id),
  versao_atual_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (tenant_id, client_id, modelo_id)
);
CREATE INDEX IF NOT EXISTS idx_anamneses_cliente ON anamneses (tenant_id, client_id);

CREATE TABLE IF NOT EXISTS versoes_de_anamnese (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  anamnese_id UUID NOT NULL REFERENCES anamneses(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  numero INTEGER NOT NULL,
  versao_do_modelo_id UUID NOT NULL REFERENCES versoes_de_modelo_de_anamnese(id),
  respostas JSONB NOT NULL,
  completa BOOLEAN NOT NULL,
  faltando JSONB NOT NULL DEFAULT '[]'::jsonb,
  canal VARCHAR(10) NOT NULL CHECK (canal IN ('SALAO', 'LINK')),
  preenchida_por UUID,
  link_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (anamnese_id, numero)
);
CREATE INDEX IF NOT EXISTS idx_versoes_de_anamnese_cliente ON versoes_de_anamnese (tenant_id, client_id, created_at DESC);
CREATE TRIGGER trg_versoes_de_anamnese_historico BEFORE UPDATE OR DELETE ON versoes_de_anamnese
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

-- Alertas manuais (os das respostas sao calculados, nao copiados).
CREATE TABLE IF NOT EXISTS alertas_do_cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ALERGIA', 'CONTRAINDICACAO', 'RESTRICAO', 'OBSERVACAO')),
  texto VARCHAR(300) NOT NULL,
  origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL' CHECK (origem IN ('MANUAL', 'INTERCORRENCIA')),
  ficha_id UUID,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  desativado_por UUID,
  desativado_em TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_alertas_do_cliente ON alertas_do_cliente (tenant_id, client_id) WHERE ativo;

-- ─── Termos e autorizacoes ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS modelos_de_documento (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  tipo VARCHAR(30) NOT NULL CHECK (tipo IN ('TERMO_PROCEDIMENTO', 'AUTORIZACAO_FOTOS', 'AUTORIZACAO_MARKETING')),
  nome VARCHAR(120) NOT NULL,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  versao_atual_id UUID,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_modelos_de_documento_tenant ON modelos_de_documento (tenant_id, tipo, ativo);

CREATE TABLE IF NOT EXISTS versoes_de_documento (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  modelo_id UUID NOT NULL REFERENCES modelos_de_documento(id),
  numero INTEGER NOT NULL,
  conteudo TEXT NOT NULL,
  hash_conteudo VARCHAR(64) NOT NULL,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (modelo_id, numero)
);
CREATE TRIGGER trg_versoes_de_documento_historico BEFORE UPDATE OR DELETE ON versoes_de_documento
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

-- O que cada servico (ou categoria) exige. nivel: ALERTAR (so pendencia) ou BLOQUEAR (nao inicia sem justificativa).
CREATE TABLE IF NOT EXISTS exigencias_do_servico (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  service_id UUID REFERENCES services(id),
  category_id UUID,
  modelo_anamnese_id UUID REFERENCES modelos_de_anamnese(id),
  termo_id UUID REFERENCES modelos_de_documento(id),
  exige_autorizacao_fotos BOOLEAN NOT NULL DEFAULT FALSE,
  ficha_assinada BOOLEAN NOT NULL DEFAULT FALSE,
  nivel VARCHAR(10) NOT NULL DEFAULT 'ALERTAR' CHECK (nivel IN ('ALERTAR', 'BLOQUEAR')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CHECK ((service_id IS NULL) <> (category_id IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_exigencias_do_servico ON exigencias_do_servico (tenant_id, service_id) WHERE service_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_exigencias_da_categoria ON exigencias_do_servico (tenant_id, category_id) WHERE category_id IS NOT NULL;

-- ─── Ficha de atendimento (1 por agendamento) ────────────────────────────────
CREATE TABLE IF NOT EXISTS fichas_de_atendimento (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  appointment_id UUID NOT NULL REFERENCES appointments(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  professional_id UUID,
  status VARCHAR(12) NOT NULL DEFAULT 'RASCUNHO' CHECK (status IN ('RASCUNHO', 'FECHADA', 'ARQUIVADA')),
  observacoes TEXT,
  opiniao_cliente TEXT,
  cuidados TEXT,
  inicio TIMESTAMPTZ,
  fim TIMESTAMPTZ,
  justificativa_inicio TEXT,
  fechada_em TIMESTAMPTZ,
  fechada_por UUID,
  arquivada_motivo VARCHAR(300),
  aviso_de_esquecida_em TIMESTAMPTZ,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (appointment_id)
);
CREATE INDEX IF NOT EXISTS idx_fichas_cliente ON fichas_de_atendimento (tenant_id, client_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fichas_rascunho ON fichas_de_atendimento (tenant_id, created_at) WHERE status = 'RASCUNHO';

-- Uma secao por servico do agendamento. produtos: [{produtoId?, nome, quantidade?}]
CREATE TABLE IF NOT EXISTS secoes_da_ficha (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  ficha_id UUID NOT NULL REFERENCES fichas_de_atendimento(id),
  appointment_item_id UUID,
  service_id UUID,
  servico_nome VARCHAR(200),
  professional_id UUID,
  procedimento TEXT,
  tecnicas TEXT,
  produtos JSONB NOT NULL DEFAULT '[]'::jsonb,
  observacoes TEXT,
  ordem INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_secoes_da_ficha ON secoes_da_ficha (ficha_id, ordem);

-- Intercorrencias no rascunho; depois de fechada, entram como adendo.
CREATE TABLE IF NOT EXISTS intercorrencias_da_ficha (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  ficha_id UUID NOT NULL REFERENCES fichas_de_atendimento(id),
  descricao TEXT NOT NULL,
  gravidade VARCHAR(10) NOT NULL CHECK (gravidade IN ('LEVE', 'MODERADA', 'GRAVE')),
  conduta TEXT,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intercorrencias_da_ficha ON intercorrencias_da_ficha (ficha_id);

CREATE TABLE IF NOT EXISTS adendos_da_ficha (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  ficha_id UUID NOT NULL REFERENCES fichas_de_atendimento(id),
  tipo VARCHAR(15) NOT NULL CHECK (tipo IN ('NOTA', 'INTERCORRENCIA', 'CORRECAO')),
  texto TEXT NOT NULL,
  gravidade VARCHAR(10) CHECK (gravidade IS NULL OR gravidade IN ('LEVE', 'MODERADA', 'GRAVE')),
  motivo VARCHAR(300),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_adendos_da_ficha ON adendos_da_ficha (ficha_id, created_at);
CREATE TRIGGER trg_adendos_da_ficha_historico BEFORE UPDATE OR DELETE ON adendos_da_ficha
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

-- ─── Arquivos protegidos (fotos e imagens de assinatura), cifrados AES-GCM ─────
CREATE TABLE IF NOT EXISTS arquivos_protegidos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  finalidade VARCHAR(20) NOT NULL CHECK (finalidade IN ('FOTO', 'ASSINATURA')),
  content_type VARCHAR(40) NOT NULL,
  tamanho INTEGER NOT NULL,
  conteudo_cifrado BYTEA NOT NULL,
  hash_conteudo VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_arquivos_protegidos_cliente ON arquivos_protegidos (tenant_id, client_id);

CREATE TABLE IF NOT EXISTS fotos_da_ficha (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  ficha_id UUID NOT NULL REFERENCES fichas_de_atendimento(id),
  secao_id UUID REFERENCES secoes_da_ficha(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  arquivo_id UUID REFERENCES arquivos_protegidos(id),
  etapa VARCHAR(10) NOT NULL CHECK (etapa IN ('ANTES', 'DURANTE', 'DEPOIS')),
  descricao VARCHAR(200),
  origem VARCHAR(10) NOT NULL DEFAULT 'GALERIA' CHECK (origem IN ('CAMERA', 'GALERIA')),
  largura INTEGER,
  altura INTEGER,
  liberada_marketing BOOLEAN NOT NULL DEFAULT FALSE,
  liberada_em TIMESTAMPTZ,
  liberada_por UUID,
  removida_em TIMESTAMPTZ,
  removida_por UUID,
  motivo_remocao VARCHAR(300),
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_fotos_da_ficha ON fotos_da_ficha (ficha_id, created_at);
CREATE INDEX IF NOT EXISTS idx_fotos_do_cliente ON fotos_da_ficha (tenant_id, client_id, created_at DESC) WHERE removida_em IS NULL;

-- ─── Link pessoal da cliente ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS links_do_cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  appointment_id UUID,
  proposito VARCHAR(20) NOT NULL CHECK (proposito IN ('PREENCHER_E_ASSINAR', 'MEUS_DOCUMENTOS')),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_em TIMESTAMPTZ NOT NULL,
  tentativas INTEGER NOT NULL DEFAULT 0,
  bloqueado_em TIMESTAMPTZ,
  conferido_em TIMESTAMPTZ,
  revogado_em TIMESTAMPTZ,
  created_by UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_links_do_cliente ON links_do_cliente (tenant_id, client_id, created_at DESC);

-- ─── Assinaturas, recusas e revogacoes (historico) ────────────────────────────
CREATE TABLE IF NOT EXISTS assinaturas_do_cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  evento VARCHAR(12) NOT NULL CHECK (evento IN ('ASSINATURA', 'RECUSA', 'REVOGACAO')),
  tipo_documento VARCHAR(30) NOT NULL CHECK (tipo_documento IN ('ANAMNESE', 'FICHA', 'TERMO_PROCEDIMENTO',
    'AUTORIZACAO_FOTOS', 'AUTORIZACAO_MARKETING')),
  modelo_documento_id UUID REFERENCES modelos_de_documento(id),
  versao_documento_id UUID REFERENCES versoes_de_documento(id),
  versao_anamnese_id UUID REFERENCES versoes_de_anamnese(id),
  ficha_id UUID REFERENCES fichas_de_atendimento(id),
  appointment_id UUID,
  retrato TEXT,
  hash_conteudo VARCHAR(64) NOT NULL,
  assinante_nome VARCHAR(160),
  assinante_documento VARCHAR(20),
  responsavel_legal BOOLEAN NOT NULL DEFAULT FALSE,
  parentesco VARCHAR(40),
  metodo VARCHAR(20) NOT NULL CHECK (metodo IN ('DESENHO_NO_SALAO', 'ACEITE_NO_SALAO', 'LINK')),
  arquivo_assinatura_id UUID REFERENCES arquivos_protegidos(id),
  ip VARCHAR(60),
  user_agent VARCHAR(300),
  usuario_presente UUID,
  link_id UUID REFERENCES links_do_cliente(id),
  motivo VARCHAR(300),
  hash_evidencia VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_assinaturas_do_cliente ON assinaturas_do_cliente (tenant_id, client_id, created_at DESC);
CREATE TRIGGER trg_assinaturas_do_cliente_historico BEFORE UPDATE OR DELETE ON assinaturas_do_cliente
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

-- ─── Retencao (anos depois do ultimo atendimento) ─────────────────────────────
ALTER TABLE tenant_operational_settings
  ADD COLUMN IF NOT EXISTS saude_retencao_anos INTEGER NOT NULL DEFAULT 5;

-- ─── Permissoes e itens de menu ───────────────────────────────────────────────
INSERT INTO permissions (id, code, description) VALUES
  (gen_random_uuid(), 'saude:ver', 'Permite ver a anamnese completa das clientes'),
  (gen_random_uuid(), 'saude:editar', 'Permite preencher e atualizar a anamnese das clientes'),
  (gen_random_uuid(), 'ficha:ver-outros', 'Permite ver as fichas de atendimento de outras profissionais'),
  (gen_random_uuid(), 'fotos:ver', 'Permite ver as fotos de antes e depois'),
  (gen_random_uuid(), 'fotos:marketing', 'Permite liberar fotos para divulgacao'),
  (gen_random_uuid(), 'documentos:gerenciar', 'Permite gerenciar modelos de anamnese, termos e exigencias por servico')
ON CONFLICT (code) DO NOTHING;

INSERT INTO item_menu (id, route, label, is_active, display_order, icon_key, sidebar_visible, created_at, updated_at) VALUES
  (gen_random_uuid(), '/clientes/saude', 'Ver e preencher anamnese', TRUE, 74, 'Users', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/clientes/fichas-de-outros', 'Ver fichas de atendimento de outras profissionais', TRUE, 75, 'Users', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/clientes/fotos', 'Ver fotos de antes e depois', TRUE, 76, 'Users', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/clientes/fotos-marketing', 'Liberar fotos para divulgação', TRUE, 77, 'Users', FALSE, NOW(), NOW()),
  (gen_random_uuid(), '/anamnese-e-termos', 'Anamnese e termos', TRUE, 146, 'Settings', TRUE, NOW(), NOW())
ON CONFLICT (route) DO NOTHING;

UPDATE item_menu filho SET parent_item_menu_id = pai.id, updated_at = NOW()
FROM item_menu pai
WHERE pai.route = '/clientes'
  AND filho.route IN ('/clientes/saude', '/clientes/fichas-de-outros', '/clientes/fotos', '/clientes/fotos-marketing');

UPDATE item_menu filho SET parent_item_menu_id = pai.id, exclusivo_do_dono = TRUE, updated_at = NOW()
FROM item_menu pai
WHERE pai.route = '/configuracoes' AND filho.route = '/anamnese-e-termos';

INSERT INTO menu_role_permissions (id, role, route, is_active, created_at, updated_at)
SELECT gen_random_uuid(), r.role, m.route, r.role = 'OWNER', NOW(), NOW()
FROM (VALUES ('/clientes/saude'), ('/clientes/fichas-de-outros'), ('/clientes/fotos'), ('/clientes/fotos-marketing'),
             ('/anamnese-e-termos')) AS m(route)
CROSS JOIN (VALUES ('OWNER'), ('PROFESSIONAL'), ('ADMIN')) AS r(role)
ON CONFLICT DO NOTHING;

INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, p.code
FROM item_menu im
JOIN (VALUES ('/clientes/saude', 'saude:ver'), ('/clientes/saude', 'saude:editar'),
             ('/clientes/fichas-de-outros', 'ficha:ver-outros'), ('/clientes/fotos', 'fotos:ver'),
             ('/clientes/fotos-marketing', 'fotos:marketing'),
             ('/anamnese-e-termos', 'documentos:gerenciar')) AS p(route, code)
  ON p.route = im.route
ON CONFLICT DO NOTHING;

-- Dono: todas. Profissional sem perfil de acesso: ver e preencher anamnese e ver fotos (o codigo limita as clientes
-- que ela atende). Com perfil, vale o que o dono der no perfil.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('saude:ver', 'saude:editar', 'ficha:ver-outros', 'fotos:ver', 'fotos:marketing',
                                 'documentos:gerenciar')
WHERE r.name = 'OWNER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('saude:ver', 'saude:editar', 'fotos:ver')
WHERE r.name = 'PROFESSIONAL'
ON CONFLICT DO NOTHING;
