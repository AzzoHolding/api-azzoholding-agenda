-- Rotas do catalogo (item_menu) que nenhuma zona tem como pagina: o editor de perfis de acesso as
-- oferecia (ex.: Relatorios > Agendamentos, No-show, Abandono, Financeiro) e quem abria dava 404.
-- Achado no teste de telas de 25/10/2026; conferido em producao pagina a pagina (404).
--
-- So saem da escolha (distribuivel) e do menu lateral (sidebar_visible). Ficam ATIVAS: o papel
-- (menu_role_permissions) e as permissoes de API (item_menu_permissao) nao mudam. Conferido antes:
-- nenhum perfil de acesso usa estes itens, e nenhuma permissao de API vem so deles.
-- /perfil-usuario fica de fora: a tela nasce junto com esta versao.
-- Sem gatilho nem CHECK em item_menu.

UPDATE item_menu
SET distribuivel = FALSE,
    sidebar_visible = FALSE,
    updated_at = now()
WHERE route IN (
  '/chat/:conversationId',
  '/clientes/:id',
  '/clientes/importacoes',
  '/clientes/importacoes/:jobId',
  '/configuracoes/admin-sistema',
  '/estoque/itens/:id/editar',
  '/estoque/itens/novo',
  '/estoque/movimentacoes/nova',
  '/estoque/pedidos-compra/:id',
  '/pos/:id',
  '/profissionais/:id/comissao',
  '/relatorio/abandono',
  '/relatorio/agendamento',
  '/relatorio/financeiro',
  '/relatorio/no-show',
  '/servicos/importacoes',
  '/servicos/importacoes/:jobId'
);
