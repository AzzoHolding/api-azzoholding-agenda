package br.com.phdigitalcode.azzo.agenda.pro.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.repository.MenuPermissionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalAccessService;

/**
 * Cobre {@link MenuRouteCache} — espelha
 * {@code modules/auth/application/MenuService.getAllowedRoutes} (o metodo cacheado
 * {@code @CacheResult(cacheName = "menu-routes-by-tenant-role")} do original).
 */
class MenuRouteCacheTest {

  private MenuPermissionRepository menuPermissionRepository;
  private FiscalAccessService fiscalAccessService;
  private MenuRouteCache cache;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    menuPermissionRepository = mock(MenuPermissionRepository.class);
    fiscalAccessService = mock(FiscalAccessService.class);
    cache = new MenuRouteCache(menuPermissionRepository, fiscalAccessService);
  }

  @Test
  void retornaListaVaziaQuandoNaoHaRotas() {
    when(menuPermissionRepository.buscarRotasPorTenantEPapel(tenantId, PapelUsuario.OWNER))
        .thenReturn(Optional.empty());

    List<String> routes = cache.getAllowedRoutes(tenantId, PapelUsuario.OWNER);

    assertThat(routes).isEmpty();
  }

  @Test
  void removeRotasFiscaisQuandoTenantNaoPodeAcessarFiscal() {
    when(menuPermissionRepository.buscarRotasPorTenantEPapel(tenantId, PapelUsuario.OWNER))
        .thenReturn(Optional.of(List.of("/dashboard", "/configuracoes/fiscal", "/fiscal/nfse/x", "/nota-fiscal")));
    when(fiscalAccessService.podeAcessarFiscal(tenantId)).thenReturn(false);

    List<String> routes = cache.getAllowedRoutes(tenantId, PapelUsuario.OWNER);

    assertThat(routes).containsExactly("/dashboard");
  }

  @Test
  void mantemRotasFiscaisQuandoTenantPodeAcessarFiscal() {
    when(menuPermissionRepository.buscarRotasPorTenantEPapel(tenantId, PapelUsuario.OWNER))
        .thenReturn(Optional.of(List.of("/dashboard", "/configuracoes/fiscal")));
    when(fiscalAccessService.podeAcessarFiscal(tenantId)).thenReturn(true);

    List<String> routes = cache.getAllowedRoutes(tenantId, PapelUsuario.OWNER);

    assertThat(routes).containsExactly("/dashboard", "/configuracoes/fiscal");
  }

  @Test
  void adicionaRaizDeRelatoriosQuandoSoHaFilhosSemARaiz() {
    when(menuPermissionRepository.buscarRotasPorTenantEPapel(tenantId, PapelUsuario.OWNER))
        .thenReturn(Optional.of(List.of("/relatorio/vendas", "/relatorio/comissoes")));
    when(fiscalAccessService.podeAcessarFiscal(tenantId)).thenReturn(true);

    List<String> routes = cache.getAllowedRoutes(tenantId, PapelUsuario.OWNER);

    assertThat(routes).containsExactly("/relatorio", "/relatorio/vendas", "/relatorio/comissoes");
  }

  @Test
  void naoDuplicaARaizDeRelatoriosQuandoJaPresente() {
    when(menuPermissionRepository.buscarRotasPorTenantEPapel(tenantId, PapelUsuario.OWNER))
        .thenReturn(Optional.of(List.of("/relatorio", "/relatorio/vendas")));
    when(fiscalAccessService.podeAcessarFiscal(tenantId)).thenReturn(true);

    List<String> routes = cache.getAllowedRoutes(tenantId, PapelUsuario.OWNER);

    assertThat(routes).containsExactly("/relatorio", "/relatorio/vendas");
  }

  @Test
  void limparCacheNaoLancaExcecao() {
    cache.limparCache();
  }
}
