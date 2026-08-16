package br.com.phdigitalcode.azzo.agenda.pro.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MenuPermissionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;

/**
 * Bean dedicado para o cache {@code menu-routes-by-tenant-role}, ja declarado antecipadamente em
 * {@code CacheConfig} (5 min TTL / 10.000 entradas) para este modulo. Espelha
 * {@code @CacheResult(cacheName = "menu-routes-by-tenant-role")} de
 * {@code modules/auth/application/MenuService.getAllowedRoutes} do original.
 *
 * <p>Extraido para um bean proprio pelo mesmo motivo de {@code RbacPermissionCache}: Spring Cache
 * e AOP baseado em proxy, e chamadas {@code this.metodo(...)} dentro da mesma classe nao passam
 * pelo proxy (self-invocation bypass). Se este metodo estivesse dentro de
 * {@code br.com.phdigitalcode.azzo.agenda.pro.service.MenuService} e fosse chamado por
 * {@code obterMenuAtual} no mesmo bean, o cache seria silenciosamente ignorado.
 */
@Component
public class MenuRouteCache {

  private static final Set<String> FISCAL_EXACT_ROUTES = Set.of(
      "/nota-fiscal",
      "/emitir-nota",
      "/apuracao-mensal");
  private static final String REPORTS_ROOT_ROUTE = "/relatorio";
  private static final String REPORTS_CHILD_PREFIX = REPORTS_ROOT_ROUTE + "/";

  private final MenuPermissionRepository menuPermissionRepository;
  private final FiscalAccessService fiscalAccessService;

  public MenuRouteCache(MenuPermissionRepository menuPermissionRepository, FiscalAccessService fiscalAccessService) {
    this.menuPermissionRepository = menuPermissionRepository;
    this.fiscalAccessService = fiscalAccessService;
  }

  @Cacheable(cacheNames = "menu-routes-by-tenant-role", key = "#tenantId + ':' + #role")
  public List<String> getAllowedRoutes(UUID tenantId, PapelUsuario role) {
    List<String> routes = menuPermissionRepository.buscarRotasPorTenantEPapel(tenantId, role).orElse(List.of());
    if (routes.isEmpty()) {
      return routes;
    }

    List<String> filteredRoutes = fiscalAccessService.podeAcessarFiscal(tenantId)
        ? routes
        : routes.stream()
        .filter(route -> !isFiscalRoute(route))
        .toList();

    return ensureReportsRootRoute(filteredRoutes);
  }

  @CacheEvict(cacheNames = "menu-routes-by-tenant-role", allEntries = true)
  public void limparCache() {
    // No-op: invalidacao via anotacao.
  }

  private boolean isFiscalRoute(String route) {
    if (route == null || route.isBlank()) {
      return false;
    }
    return route.startsWith("/configuracoes/fiscal")
        || route.startsWith("/fiscal/nfse")
        || FISCAL_EXACT_ROUTES.contains(route);
  }

  private List<String> ensureReportsRootRoute(List<String> routes) {
    boolean hasReportsRoot = routes.contains(REPORTS_ROOT_ROUTE);
    boolean hasReportsChild = routes.stream()
        .anyMatch(route -> route != null && route.startsWith(REPORTS_CHILD_PREFIX));

    if (hasReportsRoot || !hasReportsChild) {
      return routes;
    }

    LinkedHashSet<String> normalized = new LinkedHashSet<>(routes.size() + 1);
    normalized.add(REPORTS_ROOT_ROUTE);
    normalized.addAll(routes);
    return new ArrayList<>(normalized);
  }
}
