package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogItemRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogResponse;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;

/**
 * Espelha {@code modules/auth/application/MenuCatalogService.java}. CRUD do catalogo de itens de
 * menu ({@code item_menu}) e da visibilidade por papel ({@code menu_role_permissions}) para os 3
 * papeis suportados no catalogo (ADMIN/OWNER/PROFESSIONAL — FINANCE fica de fora aqui, igual ao
 * original).
 */
@Service
public class MenuCatalogService {

  private static final Logger LOG = LoggerFactory.getLogger(MenuCatalogService.class);
  private static final List<String> SUPPORTED_ROLES = List.of("ADMIN", "OWNER", "PROFESSIONAL");

  @PersistenceContext
  private EntityManager entityManager;

  private final AuthenticatedUser authenticatedUser;
  private final AuditService auditService;

  public MenuCatalogService(AuthenticatedUser authenticatedUser, AuditService auditService) {
    this.authenticatedUser = authenticatedUser;
    this.auditService = auditService;
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public MenuCatalogResponse listCatalog() {
    List<Object[]> rows = entityManager.createNativeQuery("""
        SELECT
          im.id,
          im.route,
          im.label,
          im.parent_item_menu_id,
          parent.route AS parent_route,
          parent.label AS parent_label,
          im.display_order,
          im.icon_key,
          im.is_active,
          im.sidebar_visible,
          (
            SELECT count(*)
            FROM item_menu child
            WHERE child.parent_item_menu_id = im.id
          ) AS children_count
        FROM item_menu im
        LEFT JOIN item_menu parent ON parent.id = im.parent_item_menu_id
        ORDER BY
          CASE WHEN im.parent_item_menu_id IS NULL THEN 0 ELSE 1 END,
          COALESCE(parent.display_order, im.display_order),
          COALESCE(parent.label, im.label),
          im.display_order,
          im.label
        """).getResultList();

    Map<UUID, MenuCatalogItemResponse> byId = new LinkedHashMap<>();
    for (Object[] row : rows) {
      MenuCatalogItemResponse item = new MenuCatalogItemResponse();
      item.id = toString(row[0]);
      item.route = toString(row[1]);
      item.label = toString(row[2]);
      item.parentId = toString(row[3]);
      item.parentRoute = toString(row[4]);
      item.parentLabel = toString(row[5]);
      item.displayOrder = toInt(row[6]);
      item.iconKey = toString(row[7]);
      item.active = toBoolean(row[8]);
      item.sidebarVisible = toBoolean(row[9]);
      item.childrenCount = toInt(row[10]);
      byId.put(UUID.fromString(item.id), item);
    }

    List<Object[]> roleRows = entityManager.createNativeQuery("""
        WITH supported_roles(role) AS (
          VALUES ('ADMIN'), ('OWNER'), ('PROFESSIONAL')
        ),
        latest_role_permissions AS (
          SELECT DISTINCT ON (mrp_all.route, upper(mrp_all.role))
            mrp_all.route,
            upper(mrp_all.role) AS role,
            mrp_all.is_active
          FROM menu_role_permissions mrp_all
          WHERE upper(mrp_all.role) IN ('ADMIN', 'OWNER', 'PROFESSIONAL')
          ORDER BY
            mrp_all.route,
            upper(mrp_all.role),
            CASE WHEN mrp_all.role = upper(mrp_all.role) THEN 0 ELSE 1 END,
            mrp_all.updated_at DESC NULLS LAST,
            mrp_all.created_at DESC NULLS LAST
        )
        SELECT im.id, supported_roles.role, COALESCE(mrp.is_active, FALSE)
        FROM item_menu im
        CROSS JOIN supported_roles
        LEFT JOIN latest_role_permissions mrp
          ON mrp.route = im.route
         AND mrp.role = supported_roles.role
        ORDER BY im.label, supported_roles.role
        """).getResultList();

    for (Object[] row : roleRows) {
      UUID itemId = row[0] != null ? UUID.fromString(row[0].toString()) : null;
      if (itemId == null || row[1] == null) continue;
      MenuCatalogItemResponse item = byId.get(itemId);
      if (item == null) continue;
      MenuCatalogItemResponse.RoleVisibilityResponse visibility = new MenuCatalogItemResponse.RoleVisibilityResponse();
      visibility.role = row[1].toString();
      visibility.enabled = toBoolean(row[2]);
      item.roleVisibilities.add(visibility);
    }

    MenuCatalogResponse response = new MenuCatalogResponse();
    response.items = new ArrayList<>(byId.values());
    return response;
  }

  @Transactional
  public MenuCatalogItemResponse saveCatalogItem(MenuCatalogItemRequest request) {
    if (request == null) throw new IllegalArgumentException("Payload obrigatorio");
    UUID itemId = parseUuidNullable(request.id, "id invalido");
    UUID parentId = parseUuidNullable(request.parentId, "parentId invalido");
    if (itemId != null && itemId.equals(parentId)) {
      throw new IllegalArgumentException("Item pai invalido");
    }

    String route = normalizeRoute(request.route);
    String label = requireText(request.label, "label obrigatorio");
    Integer displayOrder = request.displayOrder == null ? 0 : request.displayOrder;
    String iconKey = trimToNull(request.iconKey);
    boolean active = Boolean.TRUE.equals(request.active);
    boolean sidebarVisible = request.sidebarVisible == null || Boolean.TRUE.equals(request.sidebarVisible);
    String previousRoute = itemId != null ? findRouteById(itemId) : null;
    validateParent(parentId);
    validateUniqueRoute(itemId, route);

    if (itemId == null) {
      itemId = UUID.fromString(entityManager.createNativeQuery("""
          INSERT INTO item_menu (route, label, parent_item_menu_id, display_order, icon_key, is_active, sidebar_visible, created_at, updated_at)
          VALUES (:route, :label, :parentId, :displayOrder, :iconKey, :active, :sidebarVisible, now(), now())
          RETURNING id
          """)
          .setParameter("route", route)
          .setParameter("label", label)
          .setParameter("parentId", parentId)
          .setParameter("displayOrder", displayOrder)
          .setParameter("iconKey", iconKey)
          .setParameter("active", active)
          .setParameter("sidebarVisible", sidebarVisible)
          .getSingleResult()
          .toString());
    } else {
      int updatedRows = entityManager.createNativeQuery("""
          UPDATE item_menu
          SET route = :route,
              label = :label,
              parent_item_menu_id = :parentId,
              display_order = :displayOrder,
              icon_key = :iconKey,
              is_active = :active,
              sidebar_visible = :sidebarVisible,
              updated_at = now()
          WHERE id = :id
          """)
          .setParameter("route", route)
          .setParameter("label", label)
          .setParameter("parentId", parentId)
          .setParameter("displayOrder", displayOrder)
          .setParameter("iconKey", iconKey)
          .setParameter("active", active)
          .setParameter("sidebarVisible", sidebarVisible)
          .setParameter("id", itemId)
          .executeUpdate();
      if (updatedRows != 1) {
        throw new IllegalArgumentException("Menu nao encontrado para atualizacao");
      }
      if (previousRoute != null && !previousRoute.equals(route)) {
        entityManager.createNativeQuery("""
            UPDATE menu_role_permissions
            SET route = :newRoute,
                updated_at = now()
            WHERE route = :oldRoute
            """)
            .setParameter("newRoute", route)
            .setParameter("oldRoute", previousRoute)
            .executeUpdate();
      }
    }

    Map<String, Boolean> roleVisibilities = normalizeRoleVisibilities(request.roleVisibilities);

    for (Map.Entry<String, Boolean> roleVisibility : roleVisibilities.entrySet()) {
      entityManager.createNativeQuery("""
          INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
          VALUES (:role, :route, :enabled, now(), now())
          ON CONFLICT (role, route) DO UPDATE SET
            is_active = EXCLUDED.is_active,
            updated_at = now()
          """)
          .setParameter("role", roleVisibility.getKey())
          .setParameter("route", route)
          .setParameter("enabled", roleVisibility.getValue())
          .executeUpdate();
    }

    entityManager.flush();
    entityManager.clear();
    validarEstadoPersistido(itemId, route, roleVisibilities);
    LOG.info(
        "Catalogo de menu salvo: id={} route={} roles={} active={} sidebarVisible={}",
        itemId, route, roleVisibilities, active, sidebarVisible);
    registrarAuditoria(itemId, route, label, parentId, displayOrder, iconKey, active, sidebarVisible, roleVisibilities);
    return findCatalogItem(itemId);
  }

  private void validarEstadoPersistido(UUID itemId, String route, Map<String, Boolean> roleVisibilities) {
    Object itemCount = entityManager.createNativeQuery("""
        SELECT count(*)
        FROM item_menu
        WHERE id = :id
          AND route = :route
        """)
        .setParameter("id", itemId)
        .setParameter("route", route)
        .getSingleResult();
    if (toInt(itemCount) != 1) {
      throw new IllegalStateException("Menu nao foi persistido no catalogo");
    }

    for (Map.Entry<String, Boolean> roleVisibility : roleVisibilities.entrySet()) {
      Object saved = entityManager.createNativeQuery("""
          SELECT is_active
          FROM menu_role_permissions
          WHERE role = :role
            AND route = :route
          """)
          .setParameter("role", roleVisibility.getKey())
          .setParameter("route", route)
          .getResultStream()
          .findFirst()
          .orElse(null);
      if (saved == null || toBoolean(saved) != roleVisibility.getValue()) {
        throw new IllegalStateException("Permissao de menu nao foi persistida para role " + roleVisibility.getKey());
      }
    }
  }

  private MenuCatalogItemResponse findCatalogItem(UUID itemId) {
    return listCatalog().items.stream()
        .filter(item -> item.id.equals(itemId.toString()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Menu nao encontrado"));
  }

  private String findRouteById(UUID itemId) {
    Object route = entityManager.createNativeQuery("""
        SELECT route
        FROM item_menu
        WHERE id = :id
        """)
        .setParameter("id", itemId)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (route == null) throw new IllegalArgumentException("Menu nao encontrado");
    return route.toString();
  }

  private void registrarAuditoria(
      UUID itemId,
      String route,
      String label,
      UUID parentId,
      Integer displayOrder,
      String iconKey,
      boolean active,
      boolean sidebarVisible,
      Map<String, Boolean> roles) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = authenticatedUser.tenantIdOuNulo();
      command.actorUserId = authenticatedUser.idOuNulo();
      command.module = AuditConstants.Module.RBAC;
      command.action = "RBAC_MENU_CATALOG_UPSERT";
      command.entityType = "MENU_CATALOG";
      command.entityId = itemId.toString();
      command.sourceChannel = AuditConstants.SourceChannel.API;
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("route", route);
      payload.put("label", label);
      payload.put("parentId", parentId != null ? parentId.toString() : null);
      payload.put("displayOrder", displayOrder);
      payload.put("iconKey", iconKey);
      payload.put("active", active);
      payload.put("sidebarVisible", sidebarVisible);
      payload.put(
          "roles",
          roles.entrySet().stream()
              .map(role -> Map.of(
                  "role", role.getKey(),
                  "enabled", role.getValue()))
              .collect(Collectors.toList()));
      payload.put("recordedAt", Instant.now().toString());
      command.after = payload;
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Nao bloquear fluxo principal por auditoria.
    }
  }

  private void validateParent(UUID parentId) {
    if (parentId == null) return;
    Object exists = entityManager.createNativeQuery("""
        SELECT 1
        FROM item_menu
        WHERE id = :id
        """)
        .setParameter("id", parentId)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (exists == null) {
      throw new IllegalArgumentException("Item pai nao encontrado");
    }
  }

  private void validateUniqueRoute(UUID currentId, String route) {
    Object existingId = entityManager.createNativeQuery("""
        SELECT id
        FROM item_menu
        WHERE route = :route
        LIMIT 1
        """)
        .setParameter("route", route)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (existingId == null) return;
    UUID existingUuid = UUID.fromString(existingId.toString());
    if (currentId == null || !existingUuid.equals(currentId)) {
      throw new IllegalArgumentException("Ja existe um menu com esta rota");
    }
  }

  private String normalizeRoute(String route) {
    String normalized = requireText(route, "route obrigatoria");
    return normalized.startsWith("/") ? normalized : "/" + normalized;
  }

  private String normalizeRole(String role) {
    String normalized = requireText(role, "role obrigatoria").toUpperCase();
    if (!SUPPORTED_ROLES.contains(normalized)) {
      throw new IllegalArgumentException("role nao suportada");
    }
    return normalized;
  }

  private Map<String, Boolean> normalizeRoleVisibilities(List<MenuCatalogItemRequest.RoleVisibilityRequest> roles) {
    Map<String, Boolean> normalized = new LinkedHashMap<>();
    for (String role : SUPPORTED_ROLES) {
      normalized.put(role, false);
    }
    if (roles == null) {
      return normalized;
    }
    for (MenuCatalogItemRequest.RoleVisibilityRequest roleVisibility : roles) {
      String role = normalizeRole(roleVisibility.role);
      normalized.put(role, Boolean.TRUE.equals(roleVisibility.enabled));
    }
    return normalized;
  }

  private UUID parseUuidNullable(String value, String errorMessage) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(errorMessage);
    }
  }

  private String requireText(String value, String errorMessage) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(errorMessage);
    return normalized;
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String toString(Object value) {
    return value != null ? value.toString() : null;
  }

  private int toInt(Object value) {
    if (value == null) return 0;
    if (value instanceof Number number) return number.intValue();
    return Integer.parseInt(value.toString());
  }

  private boolean toBoolean(Object value) {
    if (value == null) return false;
    if (value instanceof Boolean bool) return bool;
    String normalized = value.toString().trim();
    return "true".equalsIgnoreCase(normalized) || "t".equalsIgnoreCase(normalized) || "1".equals(normalized);
  }
}
