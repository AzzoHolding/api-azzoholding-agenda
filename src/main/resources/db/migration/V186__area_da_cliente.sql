-- Area da cliente (PLANO-AREA-DA-CLIENTE.md, 19/11/2026).
-- A conta da cliente NAO pertence a um salao: uma pessoa, um login, varios salões (um vinculo por salao, criado pelo
-- convite daquele salao). O salao continua vendo so o proprio cadastro; nada aqui passa dados de um salao para outro.
-- Tabelas novas; as alteracoes em tabelas existentes sao so nas da V185 (CHECKs ampliados e colunas novas).

-- ─── Conta ────────────────────────────────────────────────────────────────────
-- Entra por telefone, e-mail (so depois de confirmado) ou CPF, sempre com a senha.
-- telefone: canonico 55 + DDD + numero (ou o numero estrangeiro como chegou).
-- cpf: cifrado (AES-GCM da aplicacao); cpf_busca = HMAC do CPF, so para achar a conta no login.
CREATE TABLE IF NOT EXISTS contas_de_cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nome VARCHAR(160) NOT NULL,
  telefone VARCHAR(20),
  email VARCHAR(255),
  email_confirmado_em TIMESTAMPTZ,
  cpf_cifrado TEXT,
  cpf_busca VARCHAR(64),
  senha_hash VARCHAR(100) NOT NULL,
  tentativas INTEGER NOT NULL DEFAULT 0,
  bloqueada_ate TIMESTAMPTZ,
  sessoes_revogadas_antes TIMESTAMPTZ,
  excluida_em TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_contas_de_cliente_telefone ON contas_de_cliente (telefone) WHERE telefone IS NOT NULL AND excluida_em IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_contas_de_cliente_email ON contas_de_cliente (lower(email)) WHERE email IS NOT NULL AND excluida_em IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_contas_de_cliente_cpf ON contas_de_cliente (cpf_busca) WHERE cpf_busca IS NOT NULL AND excluida_em IS NULL;

-- Um cadastro de salao (clients) tem no maximo uma conta ativa.
CREATE TABLE IF NOT EXISTS vinculos_da_conta (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conta_id UUID NOT NULL REFERENCES contas_de_cliente(id),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  link_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  desfeito_em TIMESTAMPTZ,
  desfeito_por VARCHAR(10) CHECK (desfeito_por IS NULL OR desfeito_por IN ('CLIENTE', 'SALAO', 'LGPD', 'CONTA')),
  motivo VARCHAR(300)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_vinculos_da_conta_cliente ON vinculos_da_conta (client_id) WHERE desfeito_em IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_vinculos_da_conta_salao ON vinculos_da_conta (conta_id, tenant_id) WHERE desfeito_em IS NULL;
CREATE INDEX IF NOT EXISTS idx_vinculos_da_conta_tenant ON vinculos_da_conta (tenant_id, client_id);

-- Sessao opaca (o token so existe no cookie ou no app; aqui fica o hash). 30 dias, renovada no uso.
CREATE TABLE IF NOT EXISTS sessoes_da_conta (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conta_id UUID NOT NULL REFERENCES contas_de_cliente(id),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_em TIMESTAMPTZ NOT NULL,
  ultimo_uso_em TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  revogada_em TIMESTAMPTZ,
  ip VARCHAR(60),
  navegador VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_sessoes_da_conta ON sessoes_da_conta (conta_id, created_at DESC);

-- Confirmar e-mail e criar senha nova (link por e-mail).
CREATE TABLE IF NOT EXISTS tokens_da_conta (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conta_id UUID NOT NULL REFERENCES contas_de_cliente(id),
  tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('CONFIRMAR_EMAIL', 'NOVA_SENHA')),
  email VARCHAR(255),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_em TIMESTAMPTZ NOT NULL,
  usado_em TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Termos de uso e politica de privacidade da area. A versao de 19/11/2026 e RASCUNHO (texto do dono e do juridico
-- pendente); o aceite fica guardado com o hash da versao.
CREATE TABLE IF NOT EXISTS termos_da_area_da_cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  versao VARCHAR(30) NOT NULL UNIQUE,
  conteudo TEXT NOT NULL,
  hash_conteudo VARCHAR(64) NOT NULL,
  rascunho BOOLEAN NOT NULL DEFAULT TRUE,
  vigente BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_termos_da_area_vigente ON termos_da_area_da_cliente (vigente) WHERE vigente;
CREATE TRIGGER trg_termos_da_area_historico BEFORE UPDATE OR DELETE ON termos_da_area_da_cliente
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

CREATE TABLE IF NOT EXISTS aceites_da_conta (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conta_id UUID NOT NULL REFERENCES contas_de_cliente(id),
  termo_id UUID NOT NULL REFERENCES termos_da_area_da_cliente(id),
  hash_conteudo VARCHAR(64) NOT NULL,
  ip VARCHAR(60),
  navegador VARCHAR(300),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TRIGGER trg_aceites_da_conta_historico BEFORE UPDATE OR DELETE ON aceites_da_conta
  FOR EACH ROW EXECUTE FUNCTION negar_alteracao_fora_da_lgpd();

INSERT INTO termos_da_area_da_cliente (versao, conteudo, hash_conteudo, rascunho, vigente)
SELECT '2026-11-19-rascunho', t.c, encode(sha256(convert_to(t.c, 'UTF8')), 'hex'), TRUE, TRUE
  FROM (SELECT $t$RASCUNHO — texto provisório, a ser substituído pelo texto aprovado pelo jurídico.

Termos de uso e política de privacidade da área da cliente (Azzo)

1. A área da cliente é um serviço da Azzo que mostra a você, em um só lugar, o que os salões em que você é atendida registraram sobre os seus atendimentos: agendamentos, anamneses, termos assinados, fichas, fotos e exames.
2. Cada salão é responsável pelos dados que ele registra. A Azzo guarda esses dados em nome de cada salão. Um salão não vê os dados de outro salão, nem sabe em que outros salões você tem cadastro.
3. A sua conta (nome, telefone, e-mail, CPF e senha) é guardada pela Azzo para você entrar na área. O CPF fica cifrado.
4. Você só fica ligada a um salão pelo convite que aquele salão enviar. Você pode sair de um salão ou excluir a sua conta pela própria área. Excluir a conta não apaga os registros dos salões; para isso, faça o pedido ao salão (LGPD, art. 18).
5. As assinaturas e os aceites feitos na área são assinaturas eletrônicas simples (Lei 14.063/2020), com registro de data, hora, endereço IP e navegador.
6. Mantenha a sua senha em segredo. Se suspeitar de uso indevido, troque a senha ou peça um novo convite ao salão.$t$ AS c) t
 WHERE NOT EXISTS (SELECT 1 FROM termos_da_area_da_cliente);

-- ─── Convite: novo proposito do link pessoal (V185) ─────────────────────────────
ALTER TABLE links_do_cliente DROP CONSTRAINT IF EXISTS links_do_cliente_proposito_check;
ALTER TABLE links_do_cliente ADD CONSTRAINT links_do_cliente_proposito_check
  CHECK (proposito IN ('PREENCHER_E_ASSINAR', 'MEUS_DOCUMENTOS', 'CONVITE_DA_CONTA'));
ALTER TABLE links_do_cliente ADD COLUMN IF NOT EXISTS usado_em TIMESTAMPTZ;
ALTER TABLE links_do_cliente ADD COLUMN IF NOT EXISTS conta_id UUID REFERENCES contas_de_cliente(id);

-- O que a cliente faz pela area entra com canal e metodo proprios.
ALTER TABLE versoes_de_anamnese DROP CONSTRAINT IF EXISTS versoes_de_anamnese_canal_check;
ALTER TABLE versoes_de_anamnese ADD CONSTRAINT versoes_de_anamnese_canal_check CHECK (canal IN ('SALAO', 'LINK', 'AREA'));
ALTER TABLE versoes_de_anamnese ADD COLUMN IF NOT EXISTS conta_id UUID;
ALTER TABLE assinaturas_do_cliente DROP CONSTRAINT IF EXISTS assinaturas_do_cliente_metodo_check;
ALTER TABLE assinaturas_do_cliente ADD CONSTRAINT assinaturas_do_cliente_metodo_check
  CHECK (metodo IN ('DESENHO_NO_SALAO', 'ACEITE_NO_SALAO', 'LINK', 'AREA'));
ALTER TABLE assinaturas_do_cliente ADD COLUMN IF NOT EXISTS conta_id UUID;

-- ─── Exames ──────────────────────────────────────────────────────────────────
ALTER TABLE arquivos_protegidos DROP CONSTRAINT IF EXISTS arquivos_protegidos_finalidade_check;
ALTER TABLE arquivos_protegidos ADD CONSTRAINT arquivos_protegidos_finalidade_check CHECK (finalidade IN ('FOTO', 'ASSINATURA', 'EXAME'));

CREATE TABLE IF NOT EXISTS pedidos_de_exame (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  descricao VARCHAR(300) NOT NULL,
  appointment_id UUID,
  pedido_por UUID,
  atendido_em TIMESTAMPTZ,
  cancelado_em TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pedidos_de_exame ON pedidos_de_exame (tenant_id, client_id, created_at DESC);

CREATE TABLE IF NOT EXISTS exames_da_cliente (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_id UUID NOT NULL REFERENCES clients(id),
  titulo VARCHAR(160) NOT NULL,
  tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('EXAME', 'LAUDO', 'RECEITA', 'OUTRO')),
  data_exame DATE,
  observacao VARCHAR(500),
  enviado_por VARCHAR(7) NOT NULL CHECK (enviado_por IN ('SALAO', 'CLIENTE')),
  usuario_id UUID,
  conta_id UUID,
  pedido_id UUID REFERENCES pedidos_de_exame(id),
  arquivo_id UUID NOT NULL REFERENCES arquivos_protegidos(id),
  nome_do_arquivo VARCHAR(160),
  arquivado_em TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_exames_da_cliente ON exames_da_cliente (tenant_id, client_id, created_at DESC);
