-- Consolida toda a navegacao fiscal em um unico grupo de menu.
-- Mantem as rotas existentes, mas reorganiza a hierarquia para que
-- emissao, preview, apuracao e configuracoes fiscais fiquem sob /fiscal/nfse.

UPDATE item_menu
SET label = 'Fiscal',
    icon_key = 'Receipt',
    display_order = 150,
    parent_item_menu_id = NULL,
    updated_at = NOW()
WHERE route = '/fiscal/nfse';

WITH fiscal_children(route, label, icon_key, display_order, parent_route) AS (
  VALUES
    ('/fiscal/nfse/nova', 'Nova NFS-e', 'FileText', 151, '/fiscal/nfse'),
    ('/emitir-nota', 'Emitir NF-e/NFC-e', 'FileText', 152, '/fiscal/nfse'),
    ('/nota-fiscal', 'Preview NF-e/NFC-e', 'Eye', 153, '/fiscal/nfse'),
    ('/apuracao-mensal', 'Apuracao Mensal', 'Calculator', 154, '/fiscal/nfse'),
    ('/configuracoes/fiscal/impostos', 'Impostos', 'Calculator', 155, '/fiscal/nfse'),
    ('/configuracoes/fiscal/certificados', 'Certificados', 'ShieldCheck', 156, '/fiscal/nfse'),
    ('/configuracoes/fiscal/nfse', 'Configuracao NFS-e', 'FileText', 157, '/fiscal/nfse'),
    ('/fiscal/nfse/:id', 'Detalhe da NFS-e', 'FileText', 158, '/fiscal/nfse'),
    ('/fiscal/nfse/:id/editar', 'Editar NFS-e', 'FileText', 159, '/fiscal/nfse'),
    ('/fiscal/nfse/:id/pdf', 'PDF da NFS-e', 'FileText', 160, '/fiscal/nfse')
)
UPDATE item_menu child
SET label = seed.label,
    icon_key = seed.icon_key,
    display_order = seed.display_order,
    parent_item_menu_id = parent.id,
    is_active = TRUE,
    updated_at = NOW()
FROM fiscal_children seed
JOIN item_menu parent ON parent.route = seed.parent_route
WHERE child.route = seed.route
  AND (
    child.label IS DISTINCT FROM seed.label
    OR child.icon_key IS DISTINCT FROM seed.icon_key
    OR child.display_order IS DISTINCT FROM seed.display_order
    OR child.parent_item_menu_id IS DISTINCT FROM parent.id
    OR child.is_active IS DISTINCT FROM TRUE
  );
