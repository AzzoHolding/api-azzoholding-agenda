package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.BulkMenuOverrideRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuOverrideRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.MenuDtos.MenuRoleRoutesResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Cobre {@code modules/auth/application/MenuOverrideService.java}: overrides de RBAC fino por
 * rota, escopo {@code TENANT} (tabela {@code sobreposicao_perfil_menu_empresa}) e {@code GLOBAL}
 * (tabela {@code menu_role_permissions}).
 */
class MenuOverrideServiceTest {

  private ContextoTenant contextoTenant;
  private TenantRepository tenantRepository;
  private AuthenticatedUser authenticatedUser;
  private AuditService auditService;
  private EntityManager entityManager;
  private MenuOverrideService service;
  private final UUID tenantId = UUID.randomUUID();
  private final UUID roleId = UUID.randomUUID();
  private final UUID itemMenuId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() throws Exception {
    contextoTenant = mock(ContextoTenant.class);
    tenantRepository = mock(TenantRepository.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    auditService = mock(AuditService.class);
    entityManager = mock(EntityManager.class);

    service = new MenuOverrideService(contextoTenant, tenantRepository, authenticatedUser, new ObjectMapper(), auditService);

    Field field = MenuOverrideService.class.getDeclaredField("entityManager");
    field.setAccessible(true);
    field.set(service, entityManager);

    when(authenticatedUser.idOuNulo()).thenReturn(userId);
    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  /** Roteia {@code entityManager.createNativeQuery(sql)} pelo conteudo do SQL, como o servico real faz. */
  private void stubStandardTenantOverrideQueries() {
    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      Query query = mock(Query.class);
      when(query.setParameter(anyString(), any())).thenReturn(query);

      if (sql.contains("FROM roles")) {
        when(query.getResultStream()).thenReturn(Stream.of(roleId.toString()));
        return query;
      }
      if (sql.contains("INSERT INTO item_menu")) {
        when(query.getSingleResult()).thenReturn(itemMenuId.toString());
        return query;
      }
      if (sql.contains("FROM item_menu") && sql.contains("WHERE route = :route")) {
        when(query.getResultStream()).thenReturn(Stream.of(itemMenuId.toString()));
        return query;
      }
      if (sql.contains("FROM sobreposicao_perfil_menu_empresa") && sql.contains("SELECT enabled, reason")) {
        when(query.getResultStream()).thenReturn(Stream.empty());
        return query;
      }
      if (sql.contains("INSERT INTO sobreposicao_perfil_menu_empresa")) {
        when(query.executeUpdate()).thenReturn(1);
        return query;
      }
      if (sql.contains("INSERT INTO auditoria_permissao")) {
        when(query.executeUpdate()).thenReturn(1);
        return query;
      }
      throw new AssertionError("Query nativa inesperada no teste: " + sql);
    });
  }

  @Test
  void aplicarOverrideEscopoTenantAtualizaOverrideERegistraAuditoria() {
    stubStandardTenantOverrideQueries();

    MenuOverrideRequest request = new MenuOverrideRequest();
    request.role = "owner";
    request.route = "relatorio/vendas";
    request.enabled = true;
    request.reason = "liberado manualmente";

    var response = service.aplicarOverride(request);

    assertThat(response.get("status")).isEqualTo("OK");
    assertThat(response.get("scope")).isEqualTo("TENANT");
    assertThat(response.get("tenantId")).isEqualTo(tenantId.toString());
    assertThat(response.get("role")).isEqualTo("OWNER");
    assertThat(response.get("route")).isEqualTo("/relatorio/vendas");
    assertThat(response.get("enabled")).isEqualTo(true);
    assertThat(response.get("timestamp")).isNotNull();

    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void aplicarOverrideEscopoGlobalNaoConsultaRoleNemCriaItemComOverridePorTenant() {
    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      Query query = mock(Query.class);
      when(query.setParameter(anyString(), any())).thenReturn(query);

      if (sql.contains("FROM item_menu") && sql.contains("WHERE route = :route")) {
        when(query.getResultStream()).thenReturn(Stream.of(itemMenuId.toString()));
        return query;
      }
      if (sql.contains("menu_role_permissions") && sql.contains("SELECT is_active")) {
        when(query.getResultStream()).thenReturn(Stream.empty());
        return query;
      }
      if (sql.contains("INSERT INTO menu_role_permissions")) {
        when(query.executeUpdate()).thenReturn(1);
        return query;
      }
      throw new AssertionError("Query nativa inesperada no teste (GLOBAL): " + sql);
    });

    MenuOverrideRequest request = new MenuOverrideRequest();
    request.scope = "GLOBAL";
    request.role = "admin";
    request.route = "/painel";
    request.enabled = false;

    var response = service.aplicarOverride(request);

    assertThat(response.get("scope")).isEqualTo("GLOBAL");
    assertThat(response.get("tenantId")).isNull();
    assertThat(response.get("role")).isEqualTo("ADMIN");

    // Escopo GLOBAL nao grava linha por tenant em auditoria_permissao (so o TENANT grava).
    // A auditoria central (RBAC_MENU_GLOBAL_PERMISSION_UPSERT) tambem nao chega a ser persistida
    // porque Map.of("roleId", null) lanca NPE, engolido pelo catch(Exception) do original — bug
    // preexistente preservado por fidelidade de porte (ver comentario em
    // MenuOverrideService.registrarAuditoriaCentral).
    verify(auditService, never()).recordSuccess(any());
  }

  @Test
  void aplicarOverridesEmLoteAplicaCadaItemEAgregaContagem() {
    stubStandardTenantOverrideQueries();

    BulkMenuOverrideRequest request = new BulkMenuOverrideRequest();
    request.role = "professional";
    BulkMenuOverrideRequest.MenuOverrideItem item1 = new BulkMenuOverrideRequest.MenuOverrideItem();
    item1.route = "/agenda";
    item1.enabled = true;
    BulkMenuOverrideRequest.MenuOverrideItem item2 = new BulkMenuOverrideRequest.MenuOverrideItem();
    item2.route = "/clientes";
    item2.enabled = false;
    request.items.add(item1);
    request.items.add(item2);

    var response = service.aplicarOverridesEmLote(request);

    assertThat(response.get("status")).isEqualTo("OK");
    assertThat(response.get("updated")).isEqualTo(2);
    assertThat(response.get("scope")).isEqualTo("TENANT");
    assertThat(response.get("role")).isEqualTo("PROFESSIONAL");
    verify(auditService, times(2)).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void aplicarOverridesEmLoteExigeListaDeItens() {
    BulkMenuOverrideRequest request = new BulkMenuOverrideRequest();
    request.role = "owner";

    assertThatThrownBy(() -> service.aplicarOverridesEmLote(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Lista de overrides obrigatoria");
  }

  @Test
  void resolveTargetTenantIdFalhaComTenantInexistente() {
    UUID inexistente = UUID.randomUUID();
    when(tenantRepository.findById(inexistente)).thenReturn(Optional.empty());

    MenuOverrideRequest request = new MenuOverrideRequest();
    request.role = "owner";
    request.route = "/x";
    request.enabled = true;
    request.tenantId = inexistente.toString();

    assertThatThrownBy(() -> service.aplicarOverride(request))
        .isInstanceOf(ApiClientErrorException.class)
        .hasMessage("Tenant nao encontrado")
        .extracting(ex -> ((ApiClientErrorException) ex).getStatus())
        .isEqualTo(404);
  }

  @Test
  void resolveTargetTenantIdFalhaComUuidInvalido() {
    MenuOverrideRequest request = new MenuOverrideRequest();
    request.role = "owner";
    request.route = "/x";
    request.enabled = true;
    request.tenantId = "nao-e-uuid";

    assertThatThrownBy(() -> service.aplicarOverride(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("tenantId invalido");
  }

  @Test
  void listarRotasPorRoleExigeRole() {
    assertThatThrownBy(() -> service.listarRotasPorRole(null, "TENANT", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Role obrigatoria");
  }

  @Test
  void listarRotasPorRoleEscopoTenantMapeiaLinhas() {
    when(tenantRepository.findById(any())).thenReturn(Optional.of(new Tenant()));
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    Object[] row = {"/agenda", true, true, "override manual"};
    when(query.getResultList()).thenReturn(List.<Object[]>of(row));
    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("override_tenant AS")) return query;
      throw new AssertionError("Esperava a query com escopo TENANT: " + sql);
    });

    MenuRoleRoutesResponse response = service.listarRotasPorRole("owner", "TENANT", tenantId.toString());

    assertThat(response.scope).isEqualTo("TENANT");
    assertThat(response.role).isEqualTo("OWNER");
    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).route).isEqualTo("/agenda");
    assertThat(response.items.get(0).enabled).isTrue();
    assertThat(response.items.get(0).overridden).isTrue();
    assertThat(response.items.get(0).reason).isEqualTo("override manual");
  }

  @Test
  void listarRotasPorRoleEscopoGlobalMapeiaLinhas() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    Object[] row = {"/agenda", false, false, null};
    when(query.getResultList()).thenReturn(List.<Object[]>of(row));
    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("WITH base AS") && !sql.contains("override_tenant AS")) return query;
      throw new AssertionError("Esperava a query com escopo GLOBAL: " + sql);
    });

    MenuRoleRoutesResponse response = service.listarRotasPorRole("owner", "GLOBAL", null);

    assertThat(response.scope).isEqualTo("GLOBAL");
    assertThat(response.tenantId).isNull();
    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).enabled).isFalse();
    assertThat(response.items.get(0).overridden).isFalse();
    assertThat(response.items.get(0).reason).isNull();
  }
}
