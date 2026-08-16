package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.phdigitalcode.azzo.agenda.pro.dto.SystemAdminDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.EmailTemplateConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.FeedbackSuggestion;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Product;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ProductCapability;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Usuario;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.EmailTemplateType;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.PapelUsuario;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.EmailTemplateConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.FeedbackSuggestionRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductCapabilityRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProductRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.RefreshTokenRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.UsuarioRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/** Espelha {@code modules/admin/application/SystemAdminService.java}. */
class SystemAdminServiceTest {

  private EntityManager entityManager;
  private RefreshTokenRepository refreshTokenRepository;
  private UsuarioRepository usuarioRepository;
  private TenantRepository tenantRepository;
  private ProductRepository productRepository;
  private ProductCapabilityRepository productCapabilityRepository;
  private FeedbackSuggestionRepository feedbackSuggestionRepository;
  private AuditService auditService;
  private AuthenticatedUser authenticatedUser;
  private EmailTemplateConfigRepository emailTemplateConfigRepository;
  private EmailTemplateRendererService emailTemplateRendererService;
  private SystemAdminService service;

  @BeforeEach
  void setUp() throws Exception {
    entityManager = mock(EntityManager.class);
    refreshTokenRepository = mock(RefreshTokenRepository.class);
    usuarioRepository = mock(UsuarioRepository.class);
    tenantRepository = mock(TenantRepository.class);
    productRepository = mock(ProductRepository.class);
    productCapabilityRepository = mock(ProductCapabilityRepository.class);
    feedbackSuggestionRepository = mock(FeedbackSuggestionRepository.class);
    auditService = mock(AuditService.class);
    authenticatedUser = mock(AuthenticatedUser.class);
    emailTemplateConfigRepository = mock(EmailTemplateConfigRepository.class);
    emailTemplateRendererService = mock(EmailTemplateRendererService.class);

    service = new SystemAdminService(
        refreshTokenRepository, usuarioRepository, tenantRepository, productRepository,
        productCapabilityRepository, feedbackSuggestionRepository, auditService, authenticatedUser,
        emailTemplateConfigRepository, emailTemplateRendererService);

    Field field = SystemAdminService.class.getDeclaredField("entityManager");
    field.setAccessible(true);
    field.set(service, entityManager);

    when(authenticatedUser.idOuNulo()).thenReturn(UUID.randomUUID());
    when(authenticatedUser.tenantIdOuNulo()).thenReturn(null);
    when(authenticatedUser.nomeOuAdmin()).thenReturn("ADMIN");
  }

  private Query mockQuery(Object singleResult) {
    Query query = mock(Query.class);
    when(query.getSingleResult()).thenReturn(singleResult);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    return query;
  }

  private void stubCommercialOverviewQueries(
      long totalTenants, long signups30d, long payingTenants, long planStatusCount,
      long revenueCents, long pendingCents, List<Object[]> statusRows) {
    Query totalTenantsQuery = mockQuery(totalTenants);
    Query signupsQuery = mockQuery(signups30d);
    Query payingQuery = mockQuery(payingTenants);
    Query planStatusQuery = mockQuery(planStatusCount);
    Query revenueQuery = mockQuery(revenueCents);
    Query pendingQuery = mockQuery(pendingCents);
    Query statusRowsQuery = mock(Query.class);
    when(statusRowsQuery.getResultList()).thenReturn(statusRows);

    when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("GROUP BY ps.code")) return statusRowsQuery;
      if (sql.equals("SELECT COUNT(*) FROM tenants")) return totalTenantsQuery;
      if (sql.contains("created_at >= (NOW()")) return signupsQuery;
      if (sql.contains("COUNT(DISTINCT tenant_id)")) return payingQuery;
      if (sql.contains("ps.code = :code")) return planStatusQuery;
      if (sql.contains("status IN") && sql.contains("SUM(amount_cents")) return revenueQuery;
      if (sql.contains("status = 'PENDING'")) return pendingQuery;
      return mockQuery(0L);
    });
  }

  @Test
  void commercialOverviewAgregaContadoresEReceita() {
    Object[] row1 = {"ACTIVE", 4L};
    stubCommercialOverviewQueries(10L, 2L, 3L, 4L, 150000L, 5000L, java.util.Collections.singletonList(row1));

    SystemAdminDtos.CommercialOverviewResponse response = service.commercialOverview();

    assertThat(response.totalTenants).isEqualTo(10L);
    assertThat(response.totalSignups30d).isEqualTo(2L);
    assertThat(response.payingTenants).isEqualTo(3L);
    assertThat(response.conversionRatePercent).isEqualTo(30.0d);
    assertThat(response.revenueReceived30d).isEqualByComparingTo(BigDecimal.valueOf(1500.00).setScale(2));
    assertThat(response.pendingAmount).isEqualByComparingTo(BigDecimal.valueOf(50.00).setScale(2));
    assertThat(response.tenantsByPlanStatus).hasSize(1);
    assertThat(response.tenantsByPlanStatus.get(0).planStatus).isEqualTo("ACTIVE");
  }

  @Test
  void commercialOverviewConversionRateZeroSemTenants() {
    stubCommercialOverviewQueries(0L, 0L, 0L, 0L, 0L, 0L, List.of());

    SystemAdminDtos.CommercialOverviewResponse response = service.commercialOverview();

    assertThat(response.conversionRatePercent).isEqualTo(0.0d);
  }

  @Test
  void listGlobalAuditsMapeiaLinhasEAplicaLimitePadrao() {
    Query query = mock(Query.class);
    Object[] row = {
        UUID.randomUUID(), UUID.randomUUID(), "Salao X", UUID.randomUUID(), "OWNER", "FINANCE",
        "PAYMENT_CREATED", "PAYMENT", "123", "SUCCESS", null, "req-1", "API", "203.0.113.1", Instant.now()
    };
    when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.GlobalAuditListResponse response = service.listGlobalAudits(
        null, null, null, null, null, null, null, null, null, null, null, null);

    assertThat(response.limit).isEqualTo(50);
    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).module).isEqualTo("FINANCE");
    verify(query).setParameter("limit", 50);
  }

  @Test
  void listGlobalAuditsAplicaTodosOsFiltrosOpcionais() {
    Query query = mock(Query.class);
    when(query.getResultList()).thenReturn(List.of());
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    service.listGlobalAudits(
        "2026-01-01T00:00:00Z", "2026-01-31T00:00:00Z", UUID.randomUUID().toString(), "finance",
        "payment_created", "success", "api", "payment", UUID.randomUUID().toString(), "req-1", "busca", 500);

    verify(query).setParameter("limit", 200);
    verify(query).setParameter(eq("module"), eq("FINANCE"));
    verify(query).setParameter(eq("text"), eq("%BUSCA%"));
  }

  @Test
  void getGlobalAuditDetailLancaQuandoIdInvalido() {
    assertThatThrownBy(() -> service.getGlobalAuditDetail("nao-e-uuid"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getGlobalAuditDetailLancaNotFoundQuandoEventoNaoExiste() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultStream()).thenReturn(Stream.empty());
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    String id = UUID.randomUUID().toString();
    assertThatThrownBy(() -> service.getGlobalAuditDetail(id))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void getGlobalAuditDetailRetornaDetalheCompleto() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    Object[] row = new Object[24];
    row[0] = UUID.randomUUID();
    row[9] = "SUCCESS";
    row[14] = Instant.now();
    row[20] = true;
    when(query.getResultStream()).thenReturn(java.util.Collections.singletonList(row).stream());
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.GlobalAuditDetailResponse response = service.getGlobalAuditDetail(UUID.randomUUID().toString());

    assertThat(response.status).isEqualTo("SUCCESS");
    assertThat(response.hasChanges).isTrue();
  }

  @Test
  void listGlobalSuggestionsLancaQuandoTenantIdInvalido() {
    assertThatThrownBy(() -> service.listGlobalSuggestions("nao-e-uuid", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listGlobalSuggestionsMapeiaLinhas() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    Object[] row = new Object[18];
    row[0] = UUID.randomUUID();
    row[9] = "OPEN";
    when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.GlobalSuggestionListResponse response = service.listGlobalSuggestions(null, null, null, null, null);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).status).isEqualTo("OPEN");
  }

  @Test
  void getGlobalSuggestionDetailLancaQuandoIdInvalido() {
    assertThatThrownBy(() -> service.getGlobalSuggestionDetail("xyz"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getGlobalSuggestionDetailLancaNotFoundQuandoNaoEncontrada() {
    UUID id = UUID.randomUUID();
    when(feedbackSuggestionRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getGlobalSuggestionDetail(id.toString()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void getGlobalSuggestionDetailRetornaItemComNomeDoTenant() {
    FeedbackSuggestion entity = new FeedbackSuggestion();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(UUID.randomUUID());
    entity.setStatus("OPEN");
    when(feedbackSuggestionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultStream()).thenReturn(Stream.of("Salao Y"));
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.GlobalSuggestionItem item = service.getGlobalSuggestionDetail(entity.getId().toString());

    assertThat(item.tenantName).isEqualTo("Salao Y");
  }

  @Test
  void updateGlobalSuggestionExigeRespostaOuMudancaDeStatus() {
    FeedbackSuggestion entity = new FeedbackSuggestion();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(UUID.randomUUID());
    entity.setStatus("OPEN");
    when(feedbackSuggestionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

    SystemAdminDtos.SuggestionUpdateRequest request = new SystemAdminDtos.SuggestionUpdateRequest();
    request.status = "OPEN";

    assertThatThrownBy(() -> service.updateGlobalSuggestion(entity.getId().toString(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateGlobalSuggestionRegistraRespostaEEncerra() {
    FeedbackSuggestion entity = new FeedbackSuggestion();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(UUID.randomUUID());
    entity.setStatus("OPEN");
    when(feedbackSuggestionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultStream()).thenReturn(Stream.of("Salao Z"));
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.SuggestionUpdateRequest request = new SystemAdminDtos.SuggestionUpdateRequest();
    request.status = "closed";
    request.adminResponse = "Resolvido";

    SystemAdminDtos.GlobalSuggestionItem item = service.updateGlobalSuggestion(entity.getId().toString(), request);

    assertThat(item.status).isEqualTo("CLOSED");
    assertThat(item.adminResponse).isEqualTo("Resolvido");
    assertThat(entity.getClosedAt()).isNotNull();
    assertThat(entity.getRespondedByUserName()).isEqualTo("ADMIN");
    verify(feedbackSuggestionRepository).save(entity);
  }

  @Test
  void updateGlobalSuggestionReabreEZeraClosedAt() {
    FeedbackSuggestion entity = new FeedbackSuggestion();
    entity.setId(UUID.randomUUID());
    entity.setTenantId(UUID.randomUUID());
    entity.setStatus("CLOSED");
    entity.setClosedAt(Instant.now());
    when(feedbackSuggestionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getResultStream()).thenReturn(Stream.empty());
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.SuggestionUpdateRequest request = new SystemAdminDtos.SuggestionUpdateRequest();
    request.status = "open";

    SystemAdminDtos.GlobalSuggestionItem item = service.updateGlobalSuggestion(entity.getId().toString(), request);

    assertThat(item.status).isEqualTo("OPEN");
    assertThat(entity.getClosedAt()).isNull();
  }

  @Test
  void updateGlobalSuggestionLancaParaStatusInvalido() {
    FeedbackSuggestion entity = new FeedbackSuggestion();
    entity.setId(UUID.randomUUID());
    entity.setStatus("OPEN");
    when(feedbackSuggestionRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

    SystemAdminDtos.SuggestionUpdateRequest request = new SystemAdminDtos.SuggestionUpdateRequest();
    request.status = "QUALQUER";

    assertThatThrownBy(() -> service.updateGlobalSuggestion(entity.getId().toString(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void revokeSessionsExigeTenantOuUser() {
    assertThatThrownBy(() -> service.revokeSessions(new SystemAdminDtos.RevokeSessionsRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void revokeSessionsLancaNotFoundQuandoTenantNaoExiste() {
    SystemAdminDtos.RevokeSessionsRequest request = new SystemAdminDtos.RevokeSessionsRequest();
    request.tenantId = UUID.randomUUID().toString();
    when(tenantRepository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revokeSessions(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void revokeSessionsPorTenantDelegaAoRepositorio() {
    UUID tenantId = UUID.randomUUID();
    SystemAdminDtos.RevokeSessionsRequest request = new SystemAdminDtos.RevokeSessionsRequest();
    request.tenantId = tenantId.toString();
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant()));
    when(refreshTokenRepository.revokeAllByTenant(eq(tenantId), any())).thenReturn(3L);

    SystemAdminDtos.RevokeSessionsResponse response = service.revokeSessions(request);

    assertThat(response.revokedCount).isEqualTo(3L);
    assertThat(response.tenantId).isEqualTo(tenantId.toString());
  }

  @Test
  void revokeSessionsPorUsuarioValidaPertencimentoAoTenant() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(userId);
    user.setTenantId(UUID.randomUUID());
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(user));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant()));

    SystemAdminDtos.RevokeSessionsRequest request = new SystemAdminDtos.RevokeSessionsRequest();
    request.tenantId = tenantId.toString();
    request.userId = userId.toString();

    assertThatThrownBy(() -> service.revokeSessions(request)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void revokeSessionsPorUsuarioLancaNotFoundQuandoUsuarioNaoExiste() {
    UUID userId = UUID.randomUUID();
    when(usuarioRepository.findById(userId)).thenReturn(Optional.empty());

    SystemAdminDtos.RevokeSessionsRequest request = new SystemAdminDtos.RevokeSessionsRequest();
    request.userId = userId.toString();

    assertThatThrownBy(() -> service.revokeSessions(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void listSessionsLancaQuandoTenantIdInvalido() {
    assertThatThrownBy(() -> service.listSessions("nao-e-uuid", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listSessionsMarcaAtivoQuandoNaoRevogadaENaoExpirada() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    Object[] row = {
        UUID.randomUUID(), UUID.randomUUID(), "Tenant A", UUID.randomUUID(), "Fulano",
        "fulano@x.com", "OWNER", Instant.now(), Instant.now().plusSeconds(3600), null
    };
    when(query.getResultList()).thenReturn(java.util.Collections.singletonList(row));
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.SessionListResponse response = service.listSessions(null, null, null);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).active).isTrue();
  }

  @Test
  void revokeSessionTokenLancaQuandoRefreshTokenIdInvalido() {
    SystemAdminDtos.RevokeSessionTokenRequest request = new SystemAdminDtos.RevokeSessionTokenRequest();
    request.refreshTokenId = "invalido";

    assertThatThrownBy(() -> service.revokeSessionToken(request)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void revokeSessionTokenLancaNotFoundQuandoTenantNaoExiste() {
    SystemAdminDtos.RevokeSessionTokenRequest request = new SystemAdminDtos.RevokeSessionTokenRequest();
    request.refreshTokenId = UUID.randomUUID().toString();
    request.tenantId = UUID.randomUUID().toString();
    when(tenantRepository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.revokeSessionToken(request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void revokeSessionTokenExecutaUpdate() {
    Query query = mock(Query.class);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.executeUpdate()).thenReturn(1);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);

    SystemAdminDtos.RevokeSessionTokenRequest request = new SystemAdminDtos.RevokeSessionTokenRequest();
    request.refreshTokenId = UUID.randomUUID().toString();

    SystemAdminDtos.RevokeSessionsResponse response = service.revokeSessionToken(request);

    assertThat(response.revokedCount).isEqualTo(1L);
    assertThat(response.message).contains("com sucesso");
  }

  @Test
  void resetUserMfaLancaQuandoUserIdInvalido() {
    assertThatThrownBy(() -> service.resetUserMfa("nao-e-uuid", new SystemAdminDtos.MfaResetRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resetUserMfaLancaNotFoundQuandoUsuarioNaoEncontrado() {
    UUID userId = UUID.randomUUID();
    when(usuarioRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resetUserMfa(userId.toString(), new SystemAdminDtos.MfaResetRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void resetUserMfaExigeReason() {
    UUID userId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.resetUserMfa(userId.toString(), new SystemAdminDtos.MfaResetRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resetUserMfaValidaPertencimentoAoTenant() {
    UUID userId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(userId);
    user.setTenantId(UUID.randomUUID());
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(user));

    SystemAdminDtos.MfaResetRequest request = new SystemAdminDtos.MfaResetRequest();
    request.reason = "solicitado pelo usuario";
    request.tenantId = UUID.randomUUID().toString();

    assertThatThrownBy(() -> service.resetUserMfa(userId.toString(), request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resetUserMfaLimpaMfaERevogaSessoesPorPadrao() {
    UUID userId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(userId);
    user.setTenantId(UUID.randomUUID());
    user.setRole(PapelUsuario.OWNER);
    user.setMfaEnabled(true);
    user.setMfaSecretEnc("secreto");
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(user));
    when(refreshTokenRepository.revokeAllByUser(eq(userId), any())).thenReturn(2L);

    SystemAdminDtos.MfaResetRequest request = new SystemAdminDtos.MfaResetRequest();
    request.reason = "chamado #123";

    SystemAdminDtos.MfaResetResponse response = service.resetUserMfa(userId.toString(), request);

    assertThat(user.isMfaEnabled()).isFalse();
    assertThat(user.getMfaSecretEnc()).isNull();
    assertThat(response.mfaWasEnabled).isTrue();
    assertThat(response.mfaWasEnrolled).isTrue();
    assertThat(response.revokedCount).isEqualTo(2L);
    verify(usuarioRepository).save(user);
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void resetUserMfaNaoRevogaSessoesQuandoDesabilitado() {
    UUID userId = UUID.randomUUID();
    Usuario user = new Usuario();
    user.setId(userId);
    when(usuarioRepository.findById(userId)).thenReturn(Optional.of(user));

    SystemAdminDtos.MfaResetRequest request = new SystemAdminDtos.MfaResetRequest();
    request.reason = "motivo";
    request.revokeSessions = false;

    SystemAdminDtos.MfaResetResponse response = service.resetUserMfa(userId.toString(), request);

    assertThat(response.revokedCount).isEqualTo(0L);
    verify(refreshTokenRepository, never()).revokeAllByUser(any(), any());
  }

  @Test
  void listPlansDelegaAoRepositorioComOrdenacaoAdmin() {
    Product product = buildProduct();
    when(productRepository.listarTodosOrdenadosParaAdmin()).thenReturn(List.of(product));
    when(productCapabilityRepository.findById(product.getId())).thenReturn(Optional.empty());

    SystemAdminDtos.PlanListResponse response = service.listPlans();

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).name).isEqualTo("Plano Pro");
  }

  private Product buildProduct() {
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setName("Plano Pro");
    product.setCurrency("BRL");
    product.setPriceCents(BigDecimal.TEN);
    product.setValidityMonths(1);
    product.setActive(true);
    return product;
  }

  @Test
  void createPlanValidaERegistraAuditoria() {
    when(productCapabilityRepository.findById(any())).thenReturn(Optional.empty());
    when(productRepository.findById(any())).thenAnswer(inv -> {
      Product p = buildProduct();
      p.setId(inv.getArgument(0));
      return Optional.of(p);
    });
    when(productRepository.findOutrosTrialsAtivos(any())).thenReturn(List.of());

    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    request.name = "Novo Plano";
    request.currency = "brl";
    request.price = BigDecimal.valueOf(99.9);
    request.validityMonths = 1;

    SystemAdminDtos.PlanItem item = service.createPlan(request);

    assertThat(item.currency).isEqualTo("BRL");
    verify(productRepository).save(any(Product.class));
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void createPlanLancaQuandoTrialComPreco() {
    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    request.name = "Trial";
    request.currency = "BRL";
    request.price = BigDecimal.TEN;
    request.trial = true;

    assertThatThrownBy(() -> service.createPlan(request)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createPlanLancaQuandoPayloadNulo() {
    assertThatThrownBy(() -> service.createPlan(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createPlanLancaQuandoPrecoNegativo() {
    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    request.name = "X";
    request.currency = "BRL";
    request.price = BigDecimal.valueOf(-1);

    assertThatThrownBy(() -> service.createPlan(request)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatePlanLancaQuandoPlanIdInvalido() {
    assertThatThrownBy(() -> service.updatePlan("nao-e-uuid", new SystemAdminDtos.PlanUpsertRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatePlanLancaNotFoundQuandoPlanoNaoExiste() {
    UUID id = UUID.randomUUID();
    when(productRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.updatePlan(id.toString(), new SystemAdminDtos.PlanUpsertRequest()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updatePlanDesativaOutrosTrialsAtivosQuandoVirarTrialAtivo() {
    Product product = buildProduct();
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    when(productCapabilityRepository.findById(product.getId())).thenReturn(Optional.empty());
    Product otherTrial = buildProduct();
    otherTrial.setId(UUID.randomUUID());
    otherTrial.setTrial(true);
    otherTrial.setActive(true);
    when(productRepository.findOutrosTrialsAtivos(product.getId())).thenReturn(List.of(otherTrial));

    SystemAdminDtos.PlanUpsertRequest request = new SystemAdminDtos.PlanUpsertRequest();
    request.name = "Trial";
    request.currency = "BRL";
    request.price = BigDecimal.ZERO;
    request.trial = true;

    service.updatePlan(product.getId().toString(), request);

    assertThat(otherTrial.isActive()).isFalse();
    verify(productRepository).saveAll(List.of(otherTrial));
  }

  @Test
  void updatePlanStatusExigeActive() {
    assertThatThrownBy(() -> service.updatePlanStatus(UUID.randomUUID().toString(), new SystemAdminDtos.PlanStatusUpdateRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatePlanStatusAtualiza() {
    Product product = buildProduct();
    product.setActive(false);
    when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
    when(productRepository.findOutrosTrialsAtivos(any())).thenReturn(List.of());

    SystemAdminDtos.PlanStatusUpdateRequest request = new SystemAdminDtos.PlanStatusUpdateRequest();
    request.active = true;

    SystemAdminDtos.PlanItem item = service.updatePlanStatus(product.getId().toString(), request);

    assertThat(item.active).isTrue();
    verify(productRepository).save(product);
  }

  @Test
  void listEmailTemplatesMarcaConfiguradoEAtivo() {
    EmailTemplateConfig config = new EmailTemplateConfig();
    config.setTemplateType(EmailTemplateType.PASSWORD_RESET);
    config.setActive(true);
    config.setUpdatedAt(Instant.now());
    when(emailTemplateConfigRepository.findAllGlobals()).thenReturn(List.of(config));
    EmailTemplateRendererService.TemplateDefinition definition = new EmailTemplateRendererService.TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto", "html", List.of());
    when(emailTemplateRendererService.definitions()).thenReturn(List.of(definition));

    SystemAdminDtos.EmailTemplateListResponse response = service.listEmailTemplates();

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).configured).isTrue();
    assertThat(response.items.get(0).active).isTrue();
  }

  @Test
  void getEmailTemplateLancaQuandoTypeInvalido() {
    assertThatThrownBy(() -> service.getEmailTemplate("INVALIDO")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getEmailTemplateRetornaDefaultsQuandoNaoConfigurado() {
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.empty());
    EmailTemplateRendererService.TemplateDefinition definition = new EmailTemplateRendererService.TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto padrao", "html padrao", List.of("{{userName}}"));
    when(emailTemplateRendererService.definition(EmailTemplateType.PASSWORD_RESET)).thenReturn(definition);

    SystemAdminDtos.EmailTemplateDetailResponse response = service.getEmailTemplate("password_reset");

    assertThat(response.configured).isFalse();
    assertThat(response.subjectTemplate).isEqualTo("assunto padrao");
  }

  @Test
  void saveEmailTemplateValidaCamposObrigatorios() {
    assertThatThrownBy(() -> service.saveEmailTemplate("PASSWORD_RESET", null)).isInstanceOf(IllegalArgumentException.class);

    SystemAdminDtos.EmailTemplateUpsertRequest request = new SystemAdminDtos.EmailTemplateUpsertRequest();
    assertThatThrownBy(() -> service.saveEmailTemplate("PASSWORD_RESET", request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void saveEmailTemplateValidaFormatoDeEmail() {
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.empty());
    when(emailTemplateRendererService.sanitizeHtml(any())).thenAnswer(inv -> inv.getArgument(0));

    SystemAdminDtos.EmailTemplateUpsertRequest request = new SystemAdminDtos.EmailTemplateUpsertRequest();
    request.subjectTemplate = "Assunto";
    request.htmlTemplate = "<p>html</p>";
    request.fromEmail = "invalido";

    assertThatThrownBy(() -> service.saveEmailTemplate("PASSWORD_RESET", request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void saveEmailTemplateCriaNovoQuandoNaoExiste() {
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.empty());
    when(emailTemplateRendererService.sanitizeHtml(any())).thenAnswer(inv -> inv.getArgument(0));
    EmailTemplateRendererService.TemplateDefinition definition = new EmailTemplateRendererService.TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto", "html", List.of());
    when(emailTemplateRendererService.definition(EmailTemplateType.PASSWORD_RESET)).thenReturn(definition);

    SystemAdminDtos.EmailTemplateUpsertRequest request = new SystemAdminDtos.EmailTemplateUpsertRequest();
    request.subjectTemplate = "Assunto novo";
    request.htmlTemplate = "<p>html</p>";
    request.fromEmail = "no-reply@azzo.com";

    SystemAdminDtos.EmailTemplateDetailResponse response = service.saveEmailTemplate("PASSWORD_RESET", request);

    assertThat(response.subjectTemplate).isEqualTo("Assunto novo");
    verify(emailTemplateConfigRepository).save(any(EmailTemplateConfig.class));
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void updateEmailTemplateStatusExigeActive() {
    assertThatThrownBy(() -> service.updateEmailTemplateStatus("PASSWORD_RESET", new SystemAdminDtos.EmailTemplateStatusUpdateRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateEmailTemplateStatusLancaNotFoundQuandoNaoConfigurado() {
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.empty());
    SystemAdminDtos.EmailTemplateStatusUpdateRequest request = new SystemAdminDtos.EmailTemplateStatusUpdateRequest();
    request.active = true;

    assertThatThrownBy(() -> service.updateEmailTemplateStatus("PASSWORD_RESET", request))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  @Test
  void updateEmailTemplateStatusAtualizaQuandoConfigurado() {
    EmailTemplateConfig config = new EmailTemplateConfig();
    config.setTemplateType(EmailTemplateType.PASSWORD_RESET);
    config.setActive(false);
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.of(config));
    EmailTemplateRendererService.TemplateDefinition definition = new EmailTemplateRendererService.TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto", "html", List.of());
    when(emailTemplateRendererService.definition(EmailTemplateType.PASSWORD_RESET)).thenReturn(definition);

    SystemAdminDtos.EmailTemplateStatusUpdateRequest request = new SystemAdminDtos.EmailTemplateStatusUpdateRequest();
    request.active = true;

    SystemAdminDtos.EmailTemplateDetailResponse response = service.updateEmailTemplateStatus("PASSWORD_RESET", request);

    assertThat(response.active).isTrue();
    verify(emailTemplateConfigRepository).save(config);
  }

  @Test
  void previewEmailTemplateDelegaAoRenderer() {
    EmailTemplateRendererService.RenderedTemplate rendered = new EmailTemplateRendererService.RenderedTemplate(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto", "html",
        "from@azzo.com", "Azzo", null, List.of("{{userName}}"), java.util.Map.of("userName", "Fulano"));
    when(emailTemplateRendererService.preview(eq(EmailTemplateType.PASSWORD_RESET), any())).thenReturn(rendered);

    SystemAdminDtos.EmailTemplateUpsertRequest request = new SystemAdminDtos.EmailTemplateUpsertRequest();
    request.subjectTemplate = "assunto";
    request.htmlTemplate = "html";

    SystemAdminDtos.EmailTemplatePreviewResponse response = service.previewEmailTemplate("PASSWORD_RESET", request);

    assertThat(response.subject).isEqualTo("assunto");
    assertThat(response.fromEmail).isEqualTo("from@azzo.com");
  }

  @Test
  void previewEmailTemplateLancaQuandoPayloadNulo() {
    assertThatThrownBy(() -> service.previewEmailTemplate("PASSWORD_RESET", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void restoreDefaultEmailTemplateRemoveConfigExistente() {
    EmailTemplateConfig config = new EmailTemplateConfig();
    config.setTemplateType(EmailTemplateType.PASSWORD_RESET);
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.of(config));
    EmailTemplateRendererService.TemplateDefinition definition = new EmailTemplateRendererService.TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto padrao", "html padrao", List.of());
    when(emailTemplateRendererService.definition(EmailTemplateType.PASSWORD_RESET)).thenReturn(definition);

    SystemAdminDtos.EmailTemplateDetailResponse response = service.restoreDefaultEmailTemplate("PASSWORD_RESET");

    assertThat(response.configured).isFalse();
    assertThat(response.subjectTemplate).isEqualTo("assunto padrao");
    verify(emailTemplateConfigRepository).delete(config);
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void restoreDefaultEmailTemplateNaoLancaQuandoNaoHaConfigParaRemover() {
    when(emailTemplateConfigRepository.findGlobalByType(EmailTemplateType.PASSWORD_RESET)).thenReturn(Optional.empty());
    EmailTemplateRendererService.TemplateDefinition definition = new EmailTemplateRendererService.TemplateDefinition(
        EmailTemplateType.PASSWORD_RESET, "Redefinicao de senha", "assunto padrao", "html padrao", List.of());
    when(emailTemplateRendererService.definition(EmailTemplateType.PASSWORD_RESET)).thenReturn(definition);

    service.restoreDefaultEmailTemplate("PASSWORD_RESET");

    verify(emailTemplateConfigRepository, never()).delete(any());
  }
}
