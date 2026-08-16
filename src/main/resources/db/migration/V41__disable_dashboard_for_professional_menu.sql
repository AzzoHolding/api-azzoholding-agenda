-- Remove o dashboard do pacote operacional do PROFESSIONAL.
-- Nao altera o catalogo item_menu, apenas a permissao global da role.

UPDATE menu_role_permissions
SET is_active = FALSE,
    updated_at = NOW()
WHERE upper(role) = 'PROFESSIONAL'
  AND route = '/dashboard';
