package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

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

/**
 * Espelha o contrato de {@code modules/auth/api/MenuConfigResource.java}: paths, verbos e RBAC
 * grosso (so 6 dos 8 endpoints exigem ADMIN — {@code /current} e {@code /recarregar} sao para
 * qualquer usuario autenticado).
 */
class MenuConfigControllerTest {

  private MenuService menuService;
  private PermissionService permissionService;
  private MenuOverrideService menuOverrideService;
  private MenuCatalogService menuCatalogService;
  private MenuConfigController controller;

  @BeforeEach
  void setUp() {
    menuService = mock(MenuService.class);
    permissionService = mock(PermissionService.class);
    menuOverrideService = mock(MenuOverrideService.class);
    menuCatalogService = mock(MenuCatalogService.class);
    controller = new MenuConfigController(menuService, permissionService, menuOverrideService, menuCatalogService);
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(MenuConfigController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/api/v1/config/menus");
  }

  @Test
  void classeNaoExigeAdmin() {
    assertThat(MenuConfigController.class.getAnnotation(PreAuthorize.class)).isNull();
  }

  @Test
  void currentERecarregarNaoExigemAdmin() throws NoSuchMethodException {
    assertThat(metodoExigeAdmin("obterMenuAtual")).isFalse();
    assertThat(metodoExigeAdmin("recarregarPermissoes")).isFalse();
  }

  @Test
  void endpointsAdministrativosExigemAdmin() throws NoSuchMethodException {
    assertThat(metodoExigeAdmin("aplicarOverride", MenuOverrideRequest.class)).isTrue();
    assertThat(metodoExigeAdmin("listarRotasPorRole", String.class, String.class, String.class)).isTrue();
    assertThat(metodoExigeAdmin("listarCatalogo")).isTrue();
    assertThat(metodoExigeAdmin("criarItemCatalogo", MenuCatalogItemRequest.class)).isTrue();
    assertThat(metodoExigeAdmin("atualizarItemCatalogo", String.class, MenuCatalogItemRequest.class)).isTrue();
    assertThat(metodoExigeAdmin("aplicarOverrideEmLote", BulkMenuOverrideRequest.class)).isTrue();
  }

  private boolean metodoExigeAdmin(String name, Class<?>... paramTypes) throws NoSuchMethodException {
    Method method = MenuConfigController.class.getMethod(name, paramTypes);
    PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
    return preAuthorize != null && preAuthorize.value().contains("'ADMIN'");
  }

  @Test
  void obterMenuAtualDelegaAoMenuService() {
    MenuConfigResponse expected = new MenuConfigResponse();
    when(menuService.obterMenuAtual()).thenReturn(expected);

    assertThat(controller.obterMenuAtual()).isSameAs(expected);
  }

  @Test
  void recarregarPermissoesLimpaOsDoisCachesERetornaMensagemPadrao() {
    Map<String, String> response = controller.recarregarPermissoes();

    verify(menuService).limparCacheMenuPorPapel();
    verify(permissionService).limparCachePermissoesUsuario();
    assertThat(response.get("status")).isEqualTo("OK");
    assertThat(response.get("message")).isEqualTo("Permissoes recarregadas com sucesso.");
    assertThat(response.get("timestamp")).isNotNull();
  }

  @Test
  void aplicarOverrideDelegaAoServiceELimpaOsCaches() {
    MenuOverrideRequest request = new MenuOverrideRequest();
    Map<String, Object> expected = Map.of("status", "OK");
    when(menuOverrideService.aplicarOverride(request)).thenReturn(expected);

    Map<String, Object> response = controller.aplicarOverride(request);

    assertThat(response).isSameAs(expected);
    verify(menuService).limparCacheMenuPorPapel();
    verify(permissionService).limparCachePermissoesUsuario();
  }

  @Test
  void listarRotasPorRoleDelegaAoService() {
    MenuRoleRoutesResponse expected = new MenuRoleRoutesResponse();
    when(menuOverrideService.listarRotasPorRole("OWNER", "TENANT", "tid")).thenReturn(expected);

    assertThat(controller.listarRotasPorRole("OWNER", "TENANT", "tid")).isSameAs(expected);
  }

  @Test
  void listarCatalogoDelegaAoService() {
    MenuCatalogResponse expected = new MenuCatalogResponse();
    when(menuCatalogService.listCatalog()).thenReturn(expected);

    assertThat(controller.listarCatalogo()).isSameAs(expected);
  }

  @Test
  void criarItemCatalogoDelegaAoServiceELimpaOsCaches() {
    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    MenuCatalogItemResponse expected = new MenuCatalogItemResponse();
    when(menuCatalogService.saveCatalogItem(request)).thenReturn(expected);

    assertThat(controller.criarItemCatalogo(request)).isSameAs(expected);
    verify(menuService).limparCacheMenuPorPapel();
    verify(permissionService).limparCachePermissoesUsuario();
  }

  @Test
  void atualizarItemCatalogoInjetaOIdDoPathNoRequestEDelegaAoService() {
    MenuCatalogItemRequest request = new MenuCatalogItemRequest();
    MenuCatalogItemResponse expected = new MenuCatalogItemResponse();
    when(menuCatalogService.saveCatalogItem(any(MenuCatalogItemRequest.class))).thenReturn(expected);

    MenuCatalogItemResponse response = controller.atualizarItemCatalogo("item-id-1", request);

    assertThat(response).isSameAs(expected);
    assertThat(request.id).isEqualTo("item-id-1");
    verify(menuService).limparCacheMenuPorPapel();
    verify(permissionService).limparCachePermissoesUsuario();
  }

  @Test
  void aplicarOverrideEmLoteDelegaAoServiceELimpaOsCaches() {
    BulkMenuOverrideRequest request = new BulkMenuOverrideRequest();
    Map<String, Object> expected = Map.of("status", "OK", "updated", 1);
    when(menuOverrideService.aplicarOverridesEmLote(request)).thenReturn(expected);

    Map<String, Object> response = controller.aplicarOverrideEmLote(request);

    assertThat(response).isSameAs(expected);
    verify(menuService).limparCacheMenuPorPapel();
    verify(permissionService).limparCachePermissoesUsuario();
  }
}
