package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogItemRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogResponse;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Cobre {@code modules/auth/application/MenuCatalogService.java}: CRUD do catalogo de itens de
 * menu ({@code item_menu}) e visibilidade por papel ({@code menu_role_permissions}).
 */
class MenuCatalogServiceTest {

  private AuthenticatedUser authenticatedUser;
  private AuditService auditService;
  private EntityManager entityManager;
  private MenuCatalogService service;
  private final UUID itemId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    authenticatedUser = mock(AuthenticatedUser.class);
    auditService = mock(AuditService.class);
    entityManager = mock(EntityManager.class);

    service = new MenuCatalogService(authenticatedUser, auditService);

    Field field = MenuCatalogService.class.getDeclaredField("entityManager");
    field.setAccessible(true);
    field.set(service, entityManager);

    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(authenticatedUser.tenantIdOuNulo()).thenReturn(tenantId);
  }

  private Query mockQuery() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    return query;
  }

  @Test
  void listCatalogCombinaItensERoleVisibilities() {
    Query itemsQuery = mockQuery();
    Object[] itemRow = {
        itemId.toString(), "/relatorio", "Relatorios", null, null, null, 0, "chart", true, true, 2L
    };
    when(itemsQuery.getResultList()).thenReturn(List.<Object[]>of(itemRow));

    Query roleRowsQuery = mockQuery();
    Object[] roleRow = {itemId.toString(), "OWNER", true};
    when(roleRowsQuery.getResultList()).thenReturn(List.<Object[]>of(roleRow));

    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("supported_roles")) return roleRowsQuery;
      if (sql.contains("children_count")) return itemsQuery;
      throw new AssertionError("Query inesperada: " + sql);
    });

    MenuCatalogResponse response = service.listCatalog();

    assertThat(response.items).hasSize(1);
    MenuCatalogItemResponse item = response.items.get(0);
    assertThat(item.id).isEqualTo(itemId.toString());
    assertThat(item.route).isEqualTo("/relatorio");
    assertThat(item.childrenCount).isEqualTo(2);
    assertThat(item.roleVisibilities).hasSize(1);
    assertThat(item.roleVisibilities.get(0).role).isEqualTo("OWNER");
    assertThat(item.roleVisibilities.get(0).enabled).isTrue();
  }

  @Test
  void saveCatalogItemExigePayload() {
    assertThatThrownBy(() -> service.saveCatalogItem(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Payload obrigatorio");
  }

  @Test
  void saveCatalogItemRejeitaItemComoPaiDeSiMesmo() {
    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    request.id = itemId.toString();
    request.parentId = itemId.toString();
    request.route = "/x";
    request.label = "X";
    request.active = true;

    assertThatThrownBy(() -> service.saveCatalogItem(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Item pai invalido");
  }

  @Test
  void saveCatalogItemCriaNovoItemEPersisteVisibilidadesDeRole() {
    Query routeCheckQuery = mockQuery();
    when(routeCheckQuery.getResultStream()).thenReturn(Stream.empty());

    Query insertItemQuery = mockQuery();
    when(insertItemQuery.getSingleResult()).thenReturn(itemId.toString());

    Query upsertRoleQuery = mockQuery();
    when(upsertRoleQuery.executeUpdate()).thenReturn(1);

    Query itemCountQuery = mockQuery();
    when(itemCountQuery.getSingleResult()).thenReturn(1L);

    Query roleActiveQuery = mock(Query.class);
    Map<String, Object> capturedParams = new HashMap<>();
    when(roleActiveQuery.setParameter(anyString(), any())).thenAnswer(invocation -> {
      capturedParams.put(invocation.getArgument(0), invocation.getArgument(1));
      return roleActiveQuery;
    });
    when(roleActiveQuery.getResultStream()).thenAnswer(invocation -> {
      boolean expected = "OWNER".equals(capturedParams.get("role"));
      return Stream.of(expected);
    });

    Query listItemsQuery = mockQuery();
    Object[] itemRow = {itemId.toString(), "/novo", "Novo Item", null, null, null, 0, null, true, true, 0L};
    when(listItemsQuery.getResultList()).thenReturn(List.<Object[]>of(itemRow));

    Query listRoleRowsQuery = mockQuery();
    when(listRoleRowsQuery.getResultList()).thenReturn(List.of());

    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("supported_roles")) return listRoleRowsQuery;
      if (sql.contains("children_count")) return listItemsQuery;
      if (sql.contains("INSERT INTO item_menu")) return insertItemQuery;
      if (sql.contains("FROM item_menu") && sql.contains("WHERE route = :route")) return routeCheckQuery;
      if (sql.contains("INSERT INTO menu_role_permissions")) return upsertRoleQuery;
      if (sql.contains("SELECT count(*)") && sql.contains("FROM item_menu")) return itemCountQuery;
      if (sql.contains("SELECT is_active") && sql.contains("FROM menu_role_permissions")) return roleActiveQuery;
      throw new AssertionError("Query inesperada: " + sql);
    });

    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    request.route = "novo";
    request.label = "Novo Item";
    request.displayOrder = 0;
    request.active = true;
    MenuCatalogItemRequest.RoleVisibilityRequest ownerVisibility = new MenuCatalogItemRequest.RoleVisibilityRequest();
    ownerVisibility.role = "owner";
    ownerVisibility.enabled = true;
    request.roleVisibilities.add(ownerVisibility);

    MenuCatalogItemResponse response = service.saveCatalogItem(request);

    assertThat(response.id).isEqualTo(itemId.toString());
    assertThat(response.route).isEqualTo("/novo");
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void saveCatalogItemFalhaQuandoRotaJaExisteParaOutroItem() {
    UUID outroItem = UUID.randomUUID();
    Query routeCheckQuery = mockQuery();
    when(routeCheckQuery.getResultStream()).thenReturn(Stream.of(outroItem.toString()));

    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("FROM item_menu") && sql.contains("WHERE route = :route")) return routeCheckQuery;
      throw new AssertionError("Query inesperada: " + sql);
    });

    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    request.route = "/duplicado";
    request.label = "Duplicado";
    request.displayOrder = 0;
    request.active = true;

    assertThatThrownBy(() -> service.saveCatalogItem(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Ja existe um menu com esta rota");
  }

  @Test
  void saveCatalogItemFalhaQuandoItemPaiNaoExiste() {
    UUID parentId = UUID.randomUUID();
    Query routeCheckQuery = mockQuery();
    when(routeCheckQuery.getResultStream()).thenReturn(Stream.empty());
    Query parentCheckQuery = mockQuery();
    when(parentCheckQuery.getResultStream()).thenReturn(Stream.empty());

    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("SELECT 1") && sql.contains("FROM item_menu")) return parentCheckQuery;
      if (sql.contains("FROM item_menu") && sql.contains("WHERE route = :route")) return routeCheckQuery;
      throw new AssertionError("Query inesperada: " + sql);
    });

    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    request.route = "/x";
    request.label = "X";
    request.parentId = parentId.toString();
    request.displayOrder = 0;
    request.active = true;

    assertThatThrownBy(() -> service.saveCatalogItem(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Item pai nao encontrado");
  }

  @Test
  void saveCatalogItemComIdInvalidoFalha() {
    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    request.id = "nao-e-uuid";
    request.route = "/x";
    request.label = "X";
    request.active = true;

    assertThatThrownBy(() -> service.saveCatalogItem(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("id invalido");
  }
}
