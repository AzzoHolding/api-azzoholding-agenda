-- Pacotes SaaS de menus para OWNER e PROFESSIONAL.
-- Ajusta apenas permissoes globais em menu_role_permissions.
-- Nao cria novas rotas; usa o catalogo administrativo ja existente.

WITH roles_scope(role) AS (
  VALUES
    ('OWNER'),
    ('PROFESSIONAL')
)
UPDATE menu_role_permissions mrp
SET is_active = FALSE,
    updated_at = NOW()
FROM roles_scope rs
WHERE UPPER(mrp.role) = rs.role
  AND mrp.is_active IS DISTINCT FROM FALSE;

WITH desired_permissions(role, route, is_active) AS (
  VALUES
    ('OWNER', '/dashboard', TRUE),
    ('OWNER', '/notificacoes', TRUE),
    ('OWNER', '/agenda', TRUE),
    ('OWNER', '/servicos', TRUE),
    ('OWNER', '/profissionais', TRUE),
    ('OWNER', '/profissionais/:id', TRUE),
    ('OWNER', '/clientes', TRUE),
    ('OWNER', '/clientes/:id', TRUE),
    ('OWNER', '/chat', TRUE),
    ('OWNER', '/chat/:conversationId', TRUE),
    ('OWNER', '/sugestoes', TRUE),
    ('OWNER', '/perfil-salao', TRUE),
    ('OWNER', '/configuracoes', TRUE),
    ('OWNER', '/configuracoes/integracoes/whatsapp', TRUE),
    ('OWNER', '/auditoria', TRUE),
    ('OWNER', '/auditoria/lgpd', TRUE),
    ('OWNER', '/financeiro/licenca', TRUE),
    ('PROFESSIONAL', '/notificacoes', TRUE),
    ('PROFESSIONAL', '/agenda', TRUE),
    ('PROFESSIONAL', '/clientes', TRUE),
    ('PROFESSIONAL', '/clientes/:id', TRUE),
    ('PROFESSIONAL', '/chat', TRUE),
    ('PROFESSIONAL', '/chat/:conversationId', TRUE),
    ('PROFESSIONAL', '/sugestoes', TRUE)
)
INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
SELECT role, route, is_active, NOW(), NOW()
FROM desired_permissions
ON CONFLICT (role, route) DO UPDATE
SET is_active = EXCLUDED.is_active,
    updated_at = NOW();
