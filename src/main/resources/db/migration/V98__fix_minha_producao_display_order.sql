-- Corrige display_order nulo do item /minha-producao inserido pela V97 sem este campo.
UPDATE item_menu
SET display_order = 99,
    updated_at    = NOW()
WHERE route = '/minha-producao'
  AND display_order IS NULL;
