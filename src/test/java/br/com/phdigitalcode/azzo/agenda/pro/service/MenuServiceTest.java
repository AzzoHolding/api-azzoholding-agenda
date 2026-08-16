package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuConfigItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.MenuRouteCache;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Cobre {@code modules/auth/application/MenuService.java}: resolucao do menu dinamico do usuario
 * autenticado (rotas liberadas via {@link MenuRouteCache} + catalogo ativo via query nativa).
 */
class MenuServiceTest {

  private AuthenticatedUser authenticatedUser;
  private MenuRouteCache menuRouteCache;
  private ContextoTenant contextoTenant;
  private EntityManager entityManager;
  private MenuService service;

  @BeforeEach
  void setUp() throws Exception {
    authenticatedUser = mock(AuthenticatedUser.class);
    menuRouteCache = mock(MenuRouteCache.class);
    contextoTenant = mock(ContextoTenant.class);
    entityManager = mock(EntityManager.class);

    service = new MenuService(authenticatedUser, menuRouteCache, contextoTenant);

    Field field = MenuService.class.getDeclaredField("entityManager");
    field.setAccessible(true);
    field.set(service, entityManager);
  }

  @Test
  void obterMenuAtualCombinaRoleRotasECatalogo() {
    UUID tenantId = UUID.randomUUID();
    when(authenticatedUser.roleOuNulo()).thenReturn("OWNER");
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(menuRouteCache.getAllowedRoutes(tenantId, PapelUsuario.OWNER))
        .thenReturn(List.of("/dashboard", "/clientes"));

    Query query = mock(Query.class);
    Object[] row = {
        UUID.randomUUID().toString(), "/dashboard", "Dashboard", null, 0, "home", true, true
    };
    when(query.getResultList()).thenReturn(List.<Object[]>of(row));
    when(entityManager.createNativeQuery("""
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
        """)).thenReturn(query);

    MenuConfigResponse response = service.obterMenuAtual();

    assertThat(response.role).isEqualTo("OWNER");
    assertThat(response.allowedRoutes).containsExactly("/dashboard", "/clientes");
    assertThat(response.items).hasSize(1);
    MenuConfigItemResponse item = response.items.get(0);
    assertThat(item.route).isEqualTo("/dashboard");
    assertThat(item.label).isEqualTo("Dashboard");
    assertThat(item.active).isTrue();
    assertThat(item.sidebarVisible).isTrue();
  }

  @Test
  void obterMenuAtualFalhaQuandoRoleAusenteNoJwt() {
    when(authenticatedUser.roleOuNulo()).thenReturn(null);

    assertThatThrownBy(() -> service.obterMenuAtual())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Role ausente no JWT");
  }

  @Test
  void obterMenuAtualFalhaQuandoRoleInvalidaNoJwt() {
    when(authenticatedUser.roleOuNulo()).thenReturn("NAO_EXISTE");

    assertThatThrownBy(() -> service.obterMenuAtual())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Role invalida no JWT");
  }

  @Test
  void limparCacheMenuPorPapelDelegaAoMenuRouteCache() {
    service.limparCacheMenuPorPapel();
    verify(menuRouteCache).limparCache();
  }

  @Test
  void getActiveCatalogItemsMapeiaTodosOsCamposDaLinha() {
    UUID id = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    Object[] row = {id.toString(), "/relatorio/x", "Relatorio X", parentId.toString(), 3, "chart", false, false};
    Query query = mock(Query.class);
    when(query.getResultList()).thenReturn(List.<Object[]>of(row));
    when(entityManager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(query);

    List<MenuConfigItemResponse> items = service.getActiveCatalogItems();

    assertThat(items).hasSize(1);
    MenuConfigItemResponse item = items.get(0);
    assertThat(item.id).isEqualTo(id.toString());
    assertThat(item.parentId).isEqualTo(parentId.toString());
    assertThat(item.displayOrder).isEqualTo(3);
    assertThat(item.iconKey).isEqualTo("chart");
    assertThat(item.active).isFalse();
    assertThat(item.sidebarVisible).isFalse();
  }
}
