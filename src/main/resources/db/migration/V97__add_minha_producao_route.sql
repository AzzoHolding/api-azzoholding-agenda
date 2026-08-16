-- Adiciona a rota /minha-producao (histórico pessoal de produção do PROFESSIONAL).
-- É uma rota standalone (sem parent) liberada exclusivamente para o role PROFESSIONAL.

INSERT INTO item_menu (route, label, is_active, created_at, updated_at)
VALUES ('/minha-producao', 'Minha Producao', TRUE, NOW(), NOW())
ON CONFLICT (route) DO UPDATE
  SET label      = EXCLUDED.label,
      is_active  = TRUE,
      updated_at = NOW();

INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
VALUES ('PROFESSIONAL', '/minha-producao', TRUE, NOW(), NOW())
ON CONFLICT (role, route) DO UPDATE
  SET is_active  = TRUE,
      updated_at = NOW();
