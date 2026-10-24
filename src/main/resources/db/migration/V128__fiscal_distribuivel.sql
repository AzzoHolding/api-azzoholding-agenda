-- Fiscal distribuivel nos perfis de acesso (fase F2 de docs/ESPEC_PERFIS_DE_ACESSO.md — a ultima
-- tela "em breve"). Mesmo desenho da V126/V127: papel OWNER OU a permissao, codigos fora do ADMIN.
--
-- fiscal:view le (notas, PDF, exportacao, apuracao e a LEITURA da configuracao, que a emissao usa);
-- fiscal:manage opera (rascunho, emitir, cancelar, reprocessar, recalcular a apuracao, destravar o
-- certificado). ESCREVER configuracao (impostos, certificados, NFS-e, capacidades do provedor)
-- continua so do dono: as telas /configuracoes/fiscal/* sao exclusivas (V125).

INSERT INTO permissions (id, code, description)
VALUES
  (public.uuid_generate_v4(), 'fiscal:view', 'Permite consultar notas fiscais, PDFs, exportacao contabil e apuracao'),
  (public.uuid_generate_v4(), 'fiscal:manage', 'Permite emitir, editar e cancelar notas fiscais e recalcular a apuracao')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('fiscal:view', 'fiscal:manage')
WHERE r.name = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- O fiscal esta espalhado em varias rotas no menu (/fiscal, o grupo /fiscal/nfse com o mesmo rotulo
-- "Fiscal", e /emitir-nota, /nota-fiscal, /apuracao-mensal). Escolher uma parte e nao a outra
-- quebraria a navegacao, e dois "Fiscal" no editor confundem. Por isso o dono escolhe SO /fiscal, e
-- as outras rotas o ACOMPANHAM — como o detalhe com parametro acompanha a tela de origem.
ALTER TABLE item_menu ADD COLUMN IF NOT EXISTS acompanha_rota VARCHAR(255);
COMMENT ON COLUMN item_menu.acompanha_rota IS
  'Perfis de acesso: o item nao e escolhido; vem junto com esta rota quando ela e dada.';

UPDATE item_menu SET acompanha_rota = '/fiscal', updated_at = NOW()
WHERE route IN ('/fiscal/nfse', '/fiscal/nfse/nova', '/emitir-nota', '/nota-fiscal', '/apuracao-mensal');

UPDATE item_menu SET distribuivel = TRUE, updated_at = NOW()
WHERE route IN ('/fiscal', '/nota-fiscal', '/emitir-nota', '/apuracao-mensal')
   OR route LIKE '/fiscal/%';

-- Os codigos ficam na tela escolhida; as que a acompanham entram no acesso junto com ela.
INSERT INTO item_menu_permissao (item_menu_id, permission_code)
SELECT im.id, p.code
FROM item_menu im
JOIN permissions p ON p.code IN ('fiscal:view', 'fiscal:manage')
WHERE im.route = '/fiscal'
ON CONFLICT DO NOTHING;
