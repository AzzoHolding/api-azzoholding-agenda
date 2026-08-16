-- Flag para controlar exibicao do item no menu lateral sem desativar a rota.
-- Itens com sidebar_visible = FALSE continuam ativos (RBAC e navegacao direta
-- funcionam), mas nao aparecem na sidebar. Usado inicialmente para os
-- sub-relatorios, que passaram a ser acessados pela pagina hub /relatorio.

ALTER TABLE item_menu
  ADD COLUMN IF NOT EXISTS sidebar_visible BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE item_menu
SET sidebar_visible = FALSE,
    updated_at = NOW()
WHERE route IN (
  '/relatorio/agendamento',
  '/relatorio/no-show',
  '/relatorio/abandono',
  '/relatorio/financeiro',
  '/relatorio/estoque',
  '/relatorio/vendas',
  '/relatorio/clientes',
  '/relatorio/licencas',
  '/relatorio/gerencial'
);
