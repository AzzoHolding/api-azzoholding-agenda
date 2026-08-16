-- Oculta os submenus do Financeiro no menu lateral. A pagina /financeiro
-- passou a ser a central da area, com cards de navegacao para fechamento
-- de caixa, comissoes e equipe. Rotas e RBAC permanecem intactos;
-- reversivel pelo painel Admin > Catalogo de menus.

UPDATE item_menu
SET sidebar_visible = FALSE,
    updated_at = NOW()
WHERE route IN (
  '/financeiro/fechamento-caixa',
  '/financeiro/comissoes',
  '/financeiro/profissionais'
);
