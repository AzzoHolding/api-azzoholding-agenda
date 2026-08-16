package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.BulkMenuOverrideRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuOverrideRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuRoleRouteItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuRoleRoutesResponse;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;

/**
 * Espelha {@code modules/auth/application/MenuOverrideService.java}. Permite ADMIN sobrepor a
 * visibilidade de uma rota de menu para um papel — escopo {@code TENANT} (override pontual de uma
 * empresa, tabela {@code sobreposicao_perfil_menu_empresa}) ou {@code GLOBAL} (afeta o papel em
 * todos os tenants, tabela {@code menu_role_permissions}).
 */
@Service
public class MenuOverrideService {

  @PersistenceContext
  private EntityManager entityManager;

  private final ContextoTenant contextoTenant;
  private final TenantRepository tenantRepository;
  private final AuthenticatedUser authenticatedUser;
  private final ObjectMapper objectMapper;
  private final AuditService auditService;

  public MenuOverrideService(
      ContextoTenant contextoTenant,
      TenantRepository tenantRepository,
      AuthenticatedUser authenticatedUser,
      ObjectMapper objectMapper,
      AuditService auditService) {
    this.contextoTenant = contextoTenant;
    this.tenantRepository = tenantRepository;
    this.authenticatedUser = authenticatedUser;
    this.objectMapper = objectMapper;
    this.auditService = auditService;
  }

  @Transactional
  public Map<String, Object> aplicarOverride(MenuOverrideRequest request) {
    boolean globalScope = isGlobalScope(request != null ? request.scope : null);
    UUID tenantId = globalScope ? null : resolveTargetTenantId(request != null ? request.tenantId : null);
    UUID roleId = globalScope ? null : buscarRoleId(request.role);
    UUID itemMenuId = buscarOuCriarItemMenu(request.route);

    Map<String, Object> beforeData = globalScope
        ? buscarPermissaoGlobalAtual(request.role, request.route)
        : buscarOverrideAtual(tenantId, roleId, itemMenuId);
    if (globalScope) {
      upsertPermissaoGlobal(request.role, request.route, request.enabled);
    } else {
      upsertOverride(tenantId, roleId, itemMenuId, request.enabled, request.reason);
    }
    Map<String, Object> afterData = globalScope
        ? buscarPermissaoGlobalAtual(request.role, request.route)
        : buscarOverrideAtual(tenantId, roleId, itemMenuId);

    if (!globalScope) {
      registrarAuditoria(tenantId, roleId, itemMenuId, beforeData, afterData);
    }
    registrarAuditoriaCentral(
        globalScope ? contextoTenant.obterTenantIdOuFalhar() : tenantId,
        roleId,
        itemMenuId,
        beforeData,
        afterData,
        globalScope ? "RBAC_MENU_GLOBAL_PERMISSION_UPSERT" : "RBAC_MENU_OVERRIDE_UPSERT");

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "OK");
    response.put("message", globalScope ? "Permissao global de menu salva com sucesso." : "Override de menu salvo com sucesso.");
    response.put("scope", globalScope ? "GLOBAL" : "TENANT");
    response.put("tenantId", tenantId != null ? tenantId.toString() : null);
    response.put("role", request.role.toUpperCase());
    response.put("route", normalizeRoute(request.route));
    response.put("enabled", request.enabled);
    response.put("timestamp", Instant.now().toString());
    return response;
  }

  @Transactional
  public Map<String, Object> aplicarOverridesEmLote(BulkMenuOverrideRequest request) {
    if (request == null || request.items == null || request.items.isEmpty()) {
      throw new IllegalArgumentException("Lista de overrides obrigatoria");
    }
    int updated = 0;
    for (BulkMenuOverrideRequest.MenuOverrideItem item : request.items) {
      MenuOverrideRequest single = new MenuOverrideRequest();
      single.tenantId = request.tenantId;
      single.scope = request.scope;
      single.role = request.role;
      single.route = item.route;
      single.enabled = item.enabled;
      single.reason = request.reason;
      aplicarOverride(single);
      updated++;
    }
    Map<String, Object> response = new LinkedHashMap<>();
    boolean globalScope = isGlobalScope(request.scope);
    response.put("status", "OK");
    response.put("updated", updated);
    response.put("scope", globalScope ? "GLOBAL" : "TENANT");
    response.put("tenantId", globalScope ? null : resolveTargetTenantId(request.tenantId).toString());
    response.put("role", request.role != null ? request.role.toUpperCase() : null);
    response.put("timestamp", Instant.now().toString());
    return response;
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public MenuRoleRoutesResponse listarRotasPorRole(String role, String scope, String tenantId) {
    if (role == null || role.isBlank()) throw new IllegalArgumentException("Role obrigatoria");
    boolean globalScope = isGlobalScope(scope);
    UUID targetTenantId = globalScope ? null : resolveTargetTenantId(tenantId);
    String normalizedRole = role.trim().toUpperCase();
    List<Object[]> rows = globalScope
        ? entityManager.createNativeQuery("""
        WITH base AS (
          SELECT route, is_active
          FROM menu_role_permissions
          WHERE upper(role) = :role
        ),
        catalog AS (
          SELECT route FROM base
          UNION
          SELECT route FROM item_menu WHERE is_active = TRUE
        )
        SELECT
          c.route,
          COALESCE(b.is_active, FALSE) AS enabled,
          FALSE AS overridden,
          null AS reason
        FROM catalog c
        LEFT JOIN base b ON b.route = c.route
        ORDER BY c.route
        """)
            .setParameter("role", normalizedRole)
            .getResultList()
        : entityManager.createNativeQuery("""
        WITH base AS (
          SELECT route, is_active
          FROM menu_role_permissions
          WHERE upper(role) = :role
        ),
        catalog AS (
          SELECT route FROM base
          UNION
          SELECT route FROM item_menu WHERE is_active = TRUE
        ),
        override_tenant AS (
          SELECT route, enabled, reason
          FROM (
            SELECT DISTINCT ON (im.route)
              im.route,
              spme.enabled,
              spme.reason,
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
        )
        SELECT
          c.route,
          CASE WHEN o.route IS NOT NULL THEN o.enabled ELSE COALESCE(b.is_active, FALSE) END AS enabled,
          CASE WHEN o.route IS NOT NULL THEN TRUE ELSE FALSE END AS overridden,
          o.reason
        FROM catalog c
        LEFT JOIN base b ON b.route = c.route
        LEFT JOIN override_tenant o ON o.route = c.route
        ORDER BY c.route
        """)
            .setParameter("tenantId", targetTenantId)
            .setParameter("role", normalizedRole)
            .getResultList();

    MenuRoleRoutesResponse response = new MenuRoleRoutesResponse();
    response.scope = globalScope ? "GLOBAL" : "TENANT";
    response.tenantId = targetTenantId != null ? targetTenantId.toString() : null;
    response.role = normalizedRole;
    response.items = new ArrayList<>();

    for (Object[] row : rows) {
      MenuRoleRouteItemResponse item = new MenuRoleRouteItemResponse();
      item.route = row[0] != null ? row[0].toString() : null;
      item.enabled = toBoolean(row[1]);
      item.overridden = toBoolean(row[2]);
      item.reason = row[3] != null ? row[3].toString() : null;
      response.items.add(item);
    }
    return response;
  }

  private UUID buscarRoleId(String role) {
    Object value = entityManager.createNativeQuery("""
        SELECT id
        FROM roles
        WHERE upper(name) = upper(:role)
        LIMIT 1
        """)
        .setParameter("role", role)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (value == null) throw new IllegalArgumentException("Role nao encontrada");
    return UUID.fromString(value.toString());
  }

  private UUID buscarOuCriarItemMenu(String routeRaw) {
    String route = normalizeRoute(routeRaw);
    Object existing = entityManager.createNativeQuery("""
        SELECT id
        FROM item_menu
        WHERE route = :route
        LIMIT 1
        """)
        .setParameter("route", route)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (existing != null) return UUID.fromString(existing.toString());

    Object created = entityManager.createNativeQuery("""
        INSERT INTO item_menu (route, label, is_active, created_at, updated_at)
        VALUES (:route, :label, true, now(), now())
        RETURNING id
        """)
        .setParameter("route", route)
        .setParameter("label", route)
        .getSingleResult();
    return UUID.fromString(created.toString());
  }

  private Map<String, Object> buscarOverrideAtual(UUID tenantId, UUID roleId, UUID itemMenuId) {
    Object[] row = (Object[]) entityManager.createNativeQuery("""
        SELECT enabled, reason
        FROM sobreposicao_perfil_menu_empresa
        WHERE tenant_id = :tenantId
          AND role_id = :roleId
          AND item_menu_id = :itemMenuId
        LIMIT 1
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("roleId", roleId)
        .setParameter("itemMenuId", itemMenuId)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (row == null) return null;

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("enabled", row[0]);
    map.put("reason", row[1]);
    return map;
  }

  private void upsertOverride(UUID tenantId, UUID roleId, UUID itemMenuId, boolean enabled, String reason) {
    entityManager.createNativeQuery("""
        INSERT INTO sobreposicao_perfil_menu_empresa (
          tenant_id, role_id, item_menu_id, enabled, reason, updated_by, created_at, updated_at
        )
        VALUES (:tenantId, :roleId, :itemMenuId, :enabled, :reason, :updatedBy, now(), now())
        ON CONFLICT (tenant_id, role_id, item_menu_id) DO UPDATE SET
          enabled = EXCLUDED.enabled,
          reason = EXCLUDED.reason,
          updated_by = EXCLUDED.updated_by,
          updated_at = now()
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("roleId", roleId)
        .setParameter("itemMenuId", itemMenuId)
        .setParameter("enabled", enabled)
        .setParameter("reason", trimToNull(reason))
        .setParameter("updatedBy", authenticatedUser.idOuNulo())
        .executeUpdate();
  }

  private void upsertPermissaoGlobal(String role, String routeRaw, boolean enabled) {
    String route = normalizeRoute(routeRaw);
    entityManager.createNativeQuery("""
        INSERT INTO menu_role_permissions (role, route, is_active, created_at, updated_at)
        VALUES (:role, :route, :enabled, now(), now())
        ON CONFLICT (role, route) DO UPDATE SET
          is_active = EXCLUDED.is_active,
          updated_at = now()
        """)
        .setParameter("role", role.trim().toUpperCase())
        .setParameter("route", route)
        .setParameter("enabled", enabled)
        .executeUpdate();
  }

  private Map<String, Object> buscarPermissaoGlobalAtual(String role, String routeRaw) {
    String route = normalizeRoute(routeRaw);
    Object value = entityManager.createNativeQuery("""
        SELECT is_active
        FROM menu_role_permissions
        WHERE upper(role) = upper(:role)
          AND route = :route
        LIMIT 1
        """)
        .setParameter("role", role)
        .setParameter("route", route)
        .getResultStream()
        .findFirst()
        .orElse(null);
    if (value == null) return null;
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("enabled", toBoolean(value));
    return map;
  }

  private void registrarAuditoria(
      UUID tenantId,
      UUID roleId,
      UUID itemMenuId,
      Map<String, Object> beforeData,
      Map<String, Object> afterData) {
    entityManager.createNativeQuery("""
        INSERT INTO auditoria_permissao (
          tenant_id, role_id, item_menu_id, changed_by, action, before_data, after_data, created_at
        )
        VALUES (
          :tenantId, :roleId, :itemMenuId, :changedBy, :action, cast(:beforeData as jsonb), cast(:afterData as jsonb), now()
        )
        """)
        .setParameter("tenantId", tenantId)
        .setParameter("roleId", roleId)
        .setParameter("itemMenuId", itemMenuId)
        .setParameter("changedBy", authenticatedUser.idOuNulo())
        .setParameter("action", "UPSERT_OVERRIDE")
        .setParameter("beforeData", toJson(beforeData))
        .setParameter("afterData", toJson(afterData))
        .executeUpdate();
  }

  private void registrarAuditoriaCentral(
      UUID tenantId,
      UUID roleId,
      UUID itemMenuId,
      Map<String, Object> beforeData,
      Map<String, Object> afterData,
      String action) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = authenticatedUser.idOuNulo();
      command.module = AuditConstants.Module.RBAC;
      command.action = action;
      command.entityType = "MENU_OVERRIDE";
      command.entityId = itemMenuId != null ? itemMenuId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = beforeData;
      command.after = afterData;
      // ATENCAO: Map.of(key, null) lanca NullPointerException quando roleId == null (escopo
      // GLOBAL) — bug preexistente no original, preservado por fidelidade de porte. O efeito
      // pratico e que RBAC_MENU_GLOBAL_PERMISSION_UPSERT nunca chega a ser persistido no audit
      // central (cai no catch abaixo em silencio); apenas o audit por tenant (TENANT scope, via
      // registrarAuditoria/tabela auditoria_permissao) funciona de fato. Ver
      // MIGRACAO-QUARKUS-SPRING.md.
      command.metadata = Map.of("roleId", roleId != null ? roleId.toString() : null);
      auditService.recordSuccess(command);
    } catch (Exception ignored) {
      // Auditoria secundaria nao pode bloquear fluxo principal.
    }
  }

  private String toJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao serializar dados de auditoria", e);
    }
  }

  private String normalizeRoute(String route) {
    if (route == null || route.isBlank()) throw new IllegalArgumentException("Route obrigatoria");
    String trimmed = route.trim();
    return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private boolean toBoolean(Object value) {
    if (value == null) return false;
    if (value instanceof Boolean bool) return bool;
    String normalized = value.toString().trim();
    return "true".equalsIgnoreCase(normalized) || "t".equalsIgnoreCase(normalized) || "1".equals(normalized);
  }

  private UUID resolveTargetTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return contextoTenant.obterTenantIdOuFalhar();
    }
    try {
      UUID parsed = UUID.fromString(tenantId.trim());
      if (tenantRepository.findById(parsed).isEmpty()) {
        throw new ApiClientErrorException("Tenant nao encontrado", 404);
      }
      return parsed;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("tenantId invalido");
    }
  }

  private boolean isGlobalScope(String scope) {
    return scope != null && "GLOBAL".equalsIgnoreCase(scope.trim());
  }
}
