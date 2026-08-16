package br.com.phdigitalcode.azzo.agenda.pro.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;

/**
 * Espelha {@code modules/auth/domain/repository/MenuPermissionRepository.java}. Resolve as rotas
 * de menu liberadas para um tenant+papel, combinando a permissao global de role
 * ({@code menu_role_permissions}) com o override especifico do tenant
 * ({@code sobreposicao_perfil_menu_empresa}), quando existir.
 */
@Repository
public class MenuPermissionRepository {

  @PersistenceContext
  private EntityManager entityManager;

  @SuppressWarnings("unchecked")
  public Optional<List<String>> buscarRotasPorTenantEPapel(UUID tenantId, PapelUsuario role) {
    List<String> routes = entityManager.createNativeQuery("""
        WITH base AS (
          SELECT mrp.route, TRUE AS enabled
          FROM menu_role_permissions mrp
          WHERE mrp.role = :role
            AND mrp.is_active = TRUE
        ),
        override_tenant AS (
          SELECT route, enabled
          FROM (
            SELECT DISTINCT ON (im.route)
              im.route,
              spme.enabled,
              spme.updated_at,
              spme.created_at
            FROM sobreposicao_perfil_menu_empresa spme
            JOIN roles r ON r.id = spme.role_id
            JOIN item_menu im ON im.id = spme.item_menu_id
            WHERE spme.tenant_id = :tenantId
              AND upper(r.name) = :role
              AND im.is_active = TRUE
            ORDER BY im.route, spme.updated_at DESC NULLS LAST, spme.created_at DESC NULLS LAST
          ) latest
        ),
        merged AS (
          SELECT
            COALESCE(o.route, b.route) AS route,
            CASE WHEN o.route IS NOT NULL THEN o.enabled ELSE b.enabled END AS enabled
          FROM base b
          FULL OUTER JOIN override_tenant o ON o.route = b.route
        )
        SELECT route
        FROM merged
        WHERE enabled = TRUE
        ORDER BY route
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("role", role.name())
        .getResultList();

    if (routes == null || routes.isEmpty()) return Optional.empty();
    return Optional.of(List.copyOf(routes));
  }
}
