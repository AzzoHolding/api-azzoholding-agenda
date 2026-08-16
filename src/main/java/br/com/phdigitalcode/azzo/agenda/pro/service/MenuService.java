package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuConfigItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.MenuRouteCache;

/**
 * Espelha {@code modules/auth/application/MenuService.java}. Resolve o menu dinamico do usuario
 * autenticado (rotas liberadas + catalogo ativo), fechando o gap documentado em
 * {@code AuthController} — original expunha isso via {@code GET /api/v1/config/menus/current},
 * consumido pelo frontend real.
 *
 * <p>A resolucao das rotas por tenant+papel (cacheada) vive em {@link MenuRouteCache} — extraida
 * para bean proprio para nao cair na armadilha de self-invocation do Spring AOP (ver javadoc de
 * {@link MenuRouteCache} e de {@code RbacPermissionCache}, mesmo padrao ja estabelecido para o
 * cache {@code rbac-user-permissions}).
 */
@Service
public class MenuService {

  private final AuthenticatedUser authenticatedUser;
  private final MenuRouteCache menuRouteCache;
  private final ContextoTenant contextoTenant;

  @PersistenceContext
  private EntityManager entityManager;

  public MenuService(AuthenticatedUser authenticatedUser, MenuRouteCache menuRouteCache, ContextoTenant contextoTenant) {
    this.authenticatedUser = authenticatedUser;
    this.menuRouteCache = menuRouteCache;
    this.contextoTenant = contextoTenant;
  }

  @Transactional(readOnly = true)
  public MenuConfigResponse obterMenuAtual() {
    PapelUsuario role = obterRoleOuFalhar();
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();

    MenuConfigResponse response = new MenuConfigResponse();
    response.role = role.name();
    response.allowedRoutes = menuRouteCache.getAllowedRoutes(tenantId, role);
    response.items = getActiveCatalogItems();
    return response;
  }

  public void limparCacheMenuPorPapel() {
    menuRouteCache.limparCache();
  }

  @SuppressWarnings("unchecked")
  @Transactional(readOnly = true)
  public List<MenuConfigItemResponse> getActiveCatalogItems() {
    List<Object[]> rows = entityManager.createNativeQuery("""
        SELECT
          id,
          route,
          label,
          parent_item_menu_id,
          display_order,
          icon_key,
          is_active,
          sidebar_visible
        FROM item_menu
        WHERE is_active = TRUE
        ORDER BY
          CASE WHEN parent_item_menu_id IS NULL THEN 0 ELSE 1 END,
          display_order,
          label
        """).getResultList();

    return rows.stream().map(row -> {
      MenuConfigItemResponse item = new MenuConfigItemResponse();
      item.id = row[0] != null ? row[0].toString() : null;
      item.route = row[1] != null ? row[1].toString() : null;
      item.label = row[2] != null ? row[2].toString() : null;
      item.parentId = row[3] != null ? row[3].toString() : null;
      item.displayOrder = row[4] instanceof Number number ? number.intValue() : 0;
      item.iconKey = row[5] != null ? row[5].toString() : null;
      item.active = row[6] instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(row[6]));
      item.sidebarVisible = row[7] instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(row[7]));
      return item;
    }).toList();
  }

  private PapelUsuario obterRoleOuFalhar() {
    String roleValue = authenticatedUser.roleOuNulo();
    if (roleValue == null || roleValue.isBlank()) {
      throw new IllegalStateException("Role ausente no JWT");
    }
    try {
      return PapelUsuario.valueOf(roleValue);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Role invalida no JWT: " + roleValue);
    }
  }
}
