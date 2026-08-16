package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.BulkMenuOverrideRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogItemRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogItemResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuCatalogResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuConfigResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuOverrideRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuRoleRoutesResponse;
import br.com.phdigitalcode.azzo.agenda.pro.security.PermissionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.MenuCatalogService;
import br.com.phdigitalcode.azzo.agenda.pro.service.MenuOverrideService;
import br.com.phdigitalcode.azzo.agenda.pro.service.MenuService;
import jakarta.validation.Valid;

/**
 * Espelha {@code modules/auth/api/MenuConfigResource.java} ({@code @Path("/api/v1/config/menus")},
 * {@code @Authenticated} de classe — qualquer usuario autenticado acessa {@code /current} e
 * {@code /recarregar}; os demais endpoints exigem ADMIN, igual ao original).
 *
 * <p>Fecha o gap documentado em {@code AuthController}: {@code MenuConfigResource} nunca havia
 * sido portado nesta migracao — o frontend real consumia {@code GET .../current} e recebia 404.
 * Autenticacao de qualquer usuario ja e garantida por {@code SecurityConfig} ({@code /api/v1/**}
 * exige autenticado por padrao), entao nao ha {@code @PreAuthorize} de classe aqui — apenas nos 6
 * metodos ADMIN, espelhando o {@code @RolesAllowed("ADMIN")} por metodo do original.
 */
@RestController
@RequestMapping("/api/v1/config/menus")
public class MenuConfigController {

  private final MenuService menuService;
  private final PermissionService permissionService;
  private final MenuOverrideService menuOverrideService;
  private final MenuCatalogService menuCatalogService;

  public MenuConfigController(
      MenuService menuService,
      PermissionService permissionService,
      MenuOverrideService menuOverrideService,
      MenuCatalogService menuCatalogService) {
    this.menuService = menuService;
    this.permissionService = permissionService;
    this.menuOverrideService = menuOverrideService;
    this.menuCatalogService = menuCatalogService;
  }

  @GetMapping("/current")
  public MenuConfigResponse obterMenuAtual() {
    return menuService.obterMenuAtual();
  }

  @PostMapping("/recarregar")
  public Map<String, String> recarregarPermissoes() {
    menuService.limparCacheMenuPorPapel();
    permissionService.limparCachePermissoesUsuario();

    return Map.of(
        "status", "OK",
        "message", "Permissoes recarregadas com sucesso.",
        "timestamp", Instant.now().toString());
  }

  @PostMapping("/overrides")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> aplicarOverride(@Valid @RequestBody MenuOverrideRequest request) {
    Map<String, Object> response = menuOverrideService.aplicarOverride(request);
    menuService.limparCacheMenuPorPapel();
    permissionService.limparCachePermissoesUsuario();
    return response;
  }

  @GetMapping("/roles/routes")
  @PreAuthorize("hasRole('ADMIN')")
  public MenuRoleRoutesResponse listarRotasPorRole(
      @RequestParam(name = "role", required = false) String role,
      @RequestParam(name = "scope", required = false) String scope,
      @RequestParam(name = "tenantId", required = false) String tenantId) {
    return menuOverrideService.listarRotasPorRole(role, scope, tenantId);
  }

  @GetMapping("/catalog")
  @PreAuthorize("hasRole('ADMIN')")
  public MenuCatalogResponse listarCatalogo() {
    return menuCatalogService.listCatalog();
  }

  @PostMapping("/catalog")
  @PreAuthorize("hasRole('ADMIN')")
  public MenuCatalogItemResponse criarItemCatalogo(@Valid @RequestBody MenuCatalogItemRequest request) {
    MenuCatalogItemResponse response = menuCatalogService.saveCatalogItem(request);
    menuService.limparCacheMenuPorPapel();
    permissionService.limparCachePermissoesUsuario();
    return response;
  }

  @PutMapping("/catalog/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public MenuCatalogItemResponse atualizarItemCatalogo(
      @PathVariable("id") String id,
      @Valid @RequestBody MenuCatalogItemRequest request) {
    request.id = id;
    MenuCatalogItemResponse response = menuCatalogService.saveCatalogItem(request);
    menuService.limparCacheMenuPorPapel();
    permissionService.limparCachePermissoesUsuario();
    return response;
  }

  @PostMapping("/overrides/bulk")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String, Object> aplicarOverrideEmLote(@Valid @RequestBody BulkMenuOverrideRequest request) {
    Map<String, Object> response = menuOverrideService.aplicarOverridesEmLote(request);
    menuService.limparCacheMenuPorPapel();
    permissionService.limparCachePermissoesUsuario();
    return response;
  }
}
