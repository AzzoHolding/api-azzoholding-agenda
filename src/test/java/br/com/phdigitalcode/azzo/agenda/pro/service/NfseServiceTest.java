package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import br.com.phdigitalcode.azzo.agenda.pro.dto.NfseDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseInvoiceItemEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.NfseProviderCapabilitiesEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.exception.NfseStateTransitionException;
import br.com.phdigitalcode.azzo.agenda.pro.integration.BrasilApiCnpjClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceEventRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceItemRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseInvoiceRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NfseProviderCapabilitiesRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;

/**
 * Cobre {@code modules/nfse/application/NfseService.java} (Fronteira 6, ver
 * {@code MIGRACAO-QUARKUS-SPRING.md}, Etapa 25/27) — o maior arquivo do modulo (1313L no
 * original). Nao usa Spring context (construtor manual + Mockito), mesmo padrao das fronteiras
 * anteriores de {@code nfse}.
 *
 * <p>Assimetrias do original cobertas de proposito (nao "consertadas"): {@link
 * #cancelar_seProviderFalha_naoRegistraEventoENaoRebaixaParaCancelRejected()} prova que nao ha
 * {@code try/catch} ao redor de {@code provider.cancel(...)} — a excecao propaga direto, diferente
 * de {@code autorizar} que captura para logar metricas antes de relancar.
 */
@ExtendWith(MockitoExtension.class)
class NfseServiceTest {

  @Mock private ContextoTenant contextoTenant;
  @Mock private AuthenticatedUser authenticatedUser;
  @Mock private NfseConfigRepository nfseConfigRepository;
  @Mock private NfseInvoiceRepository nfseInvoiceRepository;
  @Mock private NfseInvoiceEventRepository nfseInvoiceEventRepository;
  @Mock private NfseInvoiceItemRepository nfseInvoiceItemRepository;
  @Mock private NfseProviderCapabilitiesRepository nfseProviderCapabilitiesRepository;
  @Mock private FiscalCertificateService fiscalCertificateService;
  @Mock private NfseCertificateUnlockService nfseCertificateUnlockService;
  @Mock private NfseProviderRouterService nfseProviderRouterService;
  @Mock private NfseFiscalStateMachine nfseFiscalStateMachine;
  @Mock private NfseXmlBuilderService nfseXmlBuilderService;
  @Mock private NfseXmlSignerService nfseXmlSignerService;
  @Mock private EncryptionService encryptionService;
  @Mock private BrasilApiCnpjClient brasilApiCnpjClient;
  @Mock private NfseConfigService nfseConfigService;
  @Mock private NfseProviderAdapter providerAdapter;

  private SimpleMeterRegistry meterRegistry;
  private NfseService service;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service =
        new NfseService(
            contextoTenant,
            authenticatedUser,
            meterRegistry,
            nfseConfigRepository,
            nfseInvoiceRepository,
            nfseInvoiceEventRepository,
            nfseInvoiceItemRepository,
            nfseProviderCapabilitiesRepository,
            fiscalCertificateService,
            nfseCertificateUnlockService,
            nfseProviderRouterService,
            nfseFiscalStateMachine,
            nfseXmlBuilderService,
            nfseXmlSignerService,
            encryptionService,
            brasilApiCnpjClient,
            nfseConfigService,
            true,
            true);
    // lenient: consultarTomadorPorCnpj (fatia sem tenant, ver original) nao consome este stub
    org.mockito.Mockito.lenient().when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
  }

  // ---------------------------------------------------------------- criarRascunho

  @Test
  void criarRascunho_persisteInvoiceEItensComRpsAutomatico() {
    NfseConfigEntity config = config("3550308", "ABRASF");
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(config));
    when(nfseConfigService.resolveProvedor("ABRASF", "3550308")).thenReturn("ABRASF");
    when(nfseInvoiceRepository.nextRpsNumber(eq(tenantId), eq("3550308"), eq("1"), eq("HOMOLOGACAO")))
        .thenReturn(42);

    NfseDtos.Invoice request = invoiceRequest();

    NfseDtos.Invoice result = service.criarRascunho(request);

    assertThat(result.fiscalStatus).isEqualTo("DRAFT");
    assertThat(result.numeroRps).isEqualTo(42L);
    assertThat(result.municipioCodigoIbge).isEqualTo("3550308");
    assertThat(result.items).hasSize(1);
    verify(nfseInvoiceRepository).save(any(NfseInvoiceEntity.class));
    verify(nfseInvoiceItemRepository).save(any(NfseInvoiceItemEntity.class));
  }

  @Test
  void criarRascunho_semTomador_lancaExcecao() {
    NfseConfigEntity config = config("3550308", "ABRASF");
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(config));
    when(nfseConfigService.resolveProvedor("ABRASF", "3550308")).thenReturn("ABRASF");

    NfseDtos.Invoice request = invoiceRequest();
    request.customer = null;

    assertThatThrownBy(() -> service.criarRascunho(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tomador obrigatorio");
  }

  @Test
  void criarRascunho_municipioDivergenteDaConfig_lancaExcecao() {
    NfseConfigEntity config = config("3550308", "ABRASF");
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(config));

    NfseDtos.Invoice request = invoiceRequest();
    request.municipioCodigoIbge = "9999999";

    assertThatThrownBy(() -> service.criarRascunho(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_MUNICIPIO_DIVERGENTE_DA_CONFIG");
  }

  @Test
  void criarRascunho_semItens_lancaExcecao() {
    NfseConfigEntity config = config("3550308", "ABRASF");
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(config));
    when(nfseConfigService.resolveProvedor("ABRASF", "3550308")).thenReturn("ABRASF");

    NfseDtos.Invoice request = invoiceRequest();
    request.items = List.of();

    assertThatThrownBy(() -> service.criarRascunho(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ao menos um item");
  }

  @Test
  void criarRascunho_configAusente_lanca404() {
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.criarRascunho(invoiceRequest()))
        .isInstanceOf(ApiClientErrorException.class)
        .extracting(ex -> ((ApiClientErrorException) ex).getStatus())
        .isEqualTo(404);
  }

  // ---------------------------------------------------------------- atualizarRascunho

  @Test
  void atualizarRascunho_apenasRascunho_permiteAlteracao() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    NfseConfigEntity config = config("3550308", "ABRASF");
    when(nfseConfigRepository.findByTenantAndAmbiente(tenantId, NfseAmbiente.HOMOLOGACAO))
        .thenReturn(Optional.of(config));
    when(nfseConfigService.resolveProvedor("ABRASF", "3550308")).thenReturn("ABRASF");

    NfseDtos.Invoice result = service.atualizarRascunho(invoiceId.toString(), invoiceRequest());

    assertThat(result.id).isEqualTo(invoiceId.toString());
    verify(nfseInvoiceItemRepository).deleteByTenantAndInvoice(tenantId, invoiceId);
    verify(nfseInvoiceItemRepository).save(any(NfseInvoiceItemEntity.class));
  }

  @Test
  void atualizarRascunho_statusDiferenteDeDraft_lancaConflito() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.atualizarRascunho(invoiceId.toString(), invoiceRequest()))
        .isInstanceOf(NfseStateTransitionException.class);
  }

  @Test
  void atualizarRascunho_naoEncontrada_lanca404() {
    UUID invoiceId = UUID.randomUUID();
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.atualizarRascunho(invoiceId.toString(), invoiceRequest()))
        .isInstanceOf(ApiClientErrorException.class);
  }

  // ---------------------------------------------------------------- obterInvoice

  @Test
  void obterInvoice_encontrada_retornaComItens() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId))
        .thenReturn(List.of(itemEntity(invoiceId)));

    NfseDtos.Invoice result = service.obterInvoice(invoiceId.toString());

    assertThat(result.items).hasSize(1);
  }

  @Test
  void obterInvoice_naoEncontrada_lanca404() {
    UUID invoiceId = UUID.randomUUID();
    when(nfseInvoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.obterInvoice(invoiceId.toString()))
        .isInstanceOf(ApiClientErrorException.class);
  }

  // ---------------------------------------------------------------- autorizar

  @Test
  void autorizar_semSenhaCertificado_lancaExcecao() {
    assertThatThrownBy(() -> service.autorizar(UUID.randomUUID().toString(), new NfseDtos.AuthorizeRequest()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(FiscalCertificateService.ERR_CERTIFICATE_PASSWORD_REQUIRED);
  }

  @Test
  void autorizar_estadoInvalido_lancaConflito() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    assertThatThrownBy(() -> service.autorizar(invoiceId.toString(), request))
        .isInstanceOf(NfseStateTransitionException.class);
    verify(nfseInvoiceRepository).findByTenantAndIdForUpdate(tenantId, invoiceId);
  }

  @Test
  void autorizar_mockNacionalSelecionado_lancaExcecao() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setProvedor("MOCK_NACIONAL");
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    assertThatThrownBy(() -> service.autorizar(invoiceId.toString(), request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MOCK_NACIONAL");
  }

  @Test
  void autorizar_sucessoComProvedorReal_transicionaParaAuthorized() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity)).thenReturn("<xml/>");
    when(nfseXmlSignerService.sign("<xml/>", "senha123")).thenReturn("<xml-assinado/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    when(providerAdapter.authorize(entity, "senha123"))
        .thenReturn(
            new NfseProviderAdapter.AuthorizationResult("100", "Autorizado", "123", "PROTO-1", "CV-1", "CHAVE-1"));
    when(nfseXmlBuilderService.buildAuthorizationReturnXml(eq(entity), eq("100"), eq("Autorizado")))
        .thenReturn("<retorno/>");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    NfseDtos.Invoice result = service.autorizar(invoiceId.toString(), request);

    assertThat(result.fiscalStatus).isEqualTo("AUTHORIZED");
    assertThat(result.numeroNfse).isEqualTo("123");
    verify(fiscalCertificateService).validarSenhaCertificadoAtivo("senha123");
  }

  @Test
  void autorizar_resultadoPendente_transicionaParaPendingEDefineOperationalStatus() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity)).thenReturn("<xml/>");
    when(nfseXmlSignerService.sign(anyString(), anyString())).thenReturn("<xml-assinado/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    // status 102 = lote pendente de processamento (sinal explicito de pendencia)
    when(providerAdapter.authorize(entity, "senha123"))
        .thenReturn(new NfseProviderAdapter.AuthorizationResult("102", "Lote em processamento", null, "PROTO-1", null, null));
    when(nfseXmlBuilderService.buildAuthorizationReturnXml(eq(entity), eq("102"), anyString())).thenReturn("<retorno/>");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    NfseDtos.Invoice result = service.autorizar(invoiceId.toString(), request);

    assertThat(result.fiscalStatus).isEqualTo("PENDING");
    assertThat(result.operationalStatus).isEqualTo("WAITING_PROVIDER");
  }

  @Test
  void autorizar_provedorTeste_TEST_OK_naoChamaXmlBuilderNemSigner() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setProvedor("TEST_OK");
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    NfseDtos.Invoice result = service.autorizar(invoiceId.toString(), request);

    assertThat(result.fiscalStatus).isEqualTo("AUTHORIZED");
    verify(nfseXmlBuilderService, never()).buildAndValidateAuthorizationXml(any());
    verify(nfseXmlSignerService, never()).sign(anyString(), anyString());
    verify(nfseProviderRouterService, never()).resolve(anyString());
  }

  @Test
  void autorizar_provedorTeste_MOCK_FAIL_lancaFalhaSimulada() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setProvedor("MOCK_FAIL");
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    assertThatThrownBy(() -> service.autorizar(invoiceId.toString(), request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Falha simulada");
  }

  @Test
  void autorizar_rejeitadoAntes_reiniciaComoRetryReset() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setFiscalStatus(NfseFiscalStatus.REJECTED);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity)).thenReturn("<xml/>");
    when(nfseXmlSignerService.sign(anyString(), anyString())).thenReturn("<xml-assinado/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    when(providerAdapter.authorize(entity, "senha123"))
        .thenReturn(new NfseProviderAdapter.AuthorizationResult("100", "OK", "1", "P", "CV", "CHAVE"));
    when(nfseXmlBuilderService.buildAuthorizationReturnXml(any(), any(), any())).thenReturn("<retorno/>");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    NfseDtos.Invoice result = service.autorizar(invoiceId.toString(), request);

    assertThat(result.fiscalStatus).isEqualTo("AUTHORIZED");
  }

  @Test
  void autorizar_provedorSelecionadoIndisponivel_lancaExcecao() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("3550308", "SEFIN_NACIONAL"))
        .thenReturn(Optional.empty());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";
    request.provedor = "SEFIN_NACIONAL";

    assertThatThrownBy(() -> service.autorizar(invoiceId.toString(), request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_PROVIDER_NOT_AVAILABLE");
  }

  @Test
  void autorizar_provedorSelecionadoDisponivel_trocaProvedorDaInvoice() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    NfseProviderCapabilitiesEntity capability = new NfseProviderCapabilitiesEntity();
    capability.setProvedor("SEFIN_NACIONAL");
    when(nfseProviderCapabilitiesRepository.findByMunicipioProvedor("3550308", "SEFIN_NACIONAL"))
        .thenReturn(Optional.of(capability));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity)).thenReturn("<xml/>");
    when(nfseXmlSignerService.sign(anyString(), anyString())).thenReturn("<xml-assinado/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseProviderRouterService.resolve("SEFIN_NACIONAL")).thenReturn(providerAdapter);
    when(providerAdapter.authorize(entity, "senha123"))
        .thenReturn(new NfseProviderAdapter.AuthorizationResult("100", "OK", "1", "P", "CV", "CHAVE"));
    when(nfseXmlBuilderService.buildAuthorizationReturnXml(any(), any(), any())).thenReturn("<retorno/>");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";
    request.provedor = "SEFIN_NACIONAL";

    NfseDtos.Invoice result = service.autorizar(invoiceId.toString(), request);

    assertThat(result.provedor).isEqualTo("SEFIN_NACIONAL");
  }

  @Test
  void autorizar_providerLancaExcecao_registraMetricaFalhaERelanca() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity)).thenReturn("<xml/>");
    when(nfseXmlSignerService.sign(anyString(), anyString())).thenReturn("<xml-assinado/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    when(providerAdapter.authorize(entity, "senha123")).thenThrow(new IllegalStateException("timeout"));

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.certificatePassword = "senha123";

    assertThatThrownBy(() -> service.autorizar(invoiceId.toString(), request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("timeout");
    assertThat(meterRegistry.find("nfse.rejection.total").counter()).isNotNull();
  }

  @Test
  void autorizar_semSenhaComUnlockToken_resolveSenhaViaToken() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseCertificateUnlockService.resolvePasswordFromToken("tok-1")).thenReturn("senha-do-token");
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseXmlBuilderService.buildAndValidateAuthorizationXml(entity)).thenReturn("<xml/>");
    when(nfseXmlSignerService.sign(anyString(), eq("senha-do-token"))).thenReturn("<xml-assinado/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    when(providerAdapter.authorize(entity, "senha-do-token"))
        .thenReturn(new NfseProviderAdapter.AuthorizationResult("100", "OK", "1", "P", "CV", "CHAVE"));
    when(nfseXmlBuilderService.buildAuthorizationReturnXml(any(), any(), any())).thenReturn("<retorno/>");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.AuthorizeRequest request = new NfseDtos.AuthorizeRequest();
    request.unlockTokenId = "tok-1";

    NfseDtos.Invoice result = service.autorizar(invoiceId.toString(), request);

    assertThat(result.fiscalStatus).isEqualTo("AUTHORIZED");
    verify(fiscalCertificateService).validarSenhaCertificadoAtivo("senha-do-token");
  }

  // ---------------------------------------------------------------- cancelar

  @Test
  void cancelar_semMotivo_lancaExcecao() {
    assertThatThrownBy(() -> service.cancelar(UUID.randomUUID().toString(), new NfseDtos.CancelRequest()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cancelar_estadoInvalido_lancaConflito() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));

    NfseDtos.CancelRequest request = new NfseDtos.CancelRequest();
    request.reason = "erro de digitacao";

    assertThatThrownBy(() -> service.cancelar(invoiceId.toString(), request))
        .isInstanceOf(NfseStateTransitionException.class);
  }

  @Test
  void cancelar_sucesso_transicionaParaCancelled() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    when(providerAdapter.cancel(entity, "motivo", "senha"))
        .thenReturn(new NfseProviderAdapter.CancellationResult("200", "Cancelado"));
    when(nfseXmlBuilderService.buildCancelReturnXml(entity, "200", "Cancelado")).thenReturn("<retorno/>");
    when(encryptionService.encrypt(anyString())).thenReturn("ENC");
    when(nfseInvoiceItemRepository.listByTenantAndInvoice(tenantId, invoiceId)).thenReturn(List.of());

    NfseDtos.CancelRequest request = new NfseDtos.CancelRequest();
    request.reason = "motivo";
    request.certificatePassword = "senha";

    NfseDtos.Invoice result = service.cancelar(invoiceId.toString(), request);

    assertThat(result.fiscalStatus).isEqualTo("CANCELLED");
  }

  @Test
  void cancelar_seProviderFalha_naoRegistraEventoENaoRebaixaParaCancelRejected() {
    // Assimetria do original (Etapa 25, achado 1): sem try/catch ao redor de provider.cancel —
    // a excecao propaga direto, a invoice fica CANCEL_PENDING em memoria mas a transacao inteira
    // sofre rollback (fora do escopo deste teste unitario, que nao usa @Transactional real).
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    when(nfseInvoiceRepository.findByTenantAndIdForUpdate(tenantId, invoiceId)).thenReturn(Optional.of(entity));
    when(nfseFiscalStateMachine.canReach(any(), any())).thenReturn(true);
    when(nfseProviderRouterService.resolve("ABRASF")).thenReturn(providerAdapter);
    when(providerAdapter.cancel(entity, "motivo", "senha")).thenThrow(new IllegalStateException("provider indisponivel"));

    NfseDtos.CancelRequest request = new NfseDtos.CancelRequest();
    request.reason = "motivo";
    request.certificatePassword = "senha";

    assertThatThrownBy(() -> service.cancelar(invoiceId.toString(), request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider indisponivel");
    // apenas o evento CANCEL_PENDING foi registrado (transicao antes da chamada ao provider);
    // CANCEL_SUCCESS nunca e registrado porque o metodo nao tem try/catch ao redor do provider
    verify(nfseInvoiceEventRepository, times(1)).save(any());
    assertThat(entity.getFiscalStatus()).isEqualTo(NfseFiscalStatus.CANCEL_PENDING);
  }

  // ---------------------------------------------------------------- listarInvoices

  @Test
  void listarInvoices_semStatus_usaPaginacaoPadrao() {
    Page<NfseInvoiceEntity> page = new PageImpl<>(List.of(draftEntity(UUID.randomUUID())), PageRequest.of(0, 20), 1);
    when(nfseInvoiceRepository.pageByTenantAndOptionalStatus(tenantId, null, 1, 20)).thenReturn(page);

    NfseDtos.InvoiceListResponse response = service.listarInvoices(null, null, null);

    assertThat(response.items).hasSize(1);
    assertThat(response.total).isEqualTo(1);
    assertThat(response.page).isEqualTo(1);
    assertThat(response.pageSize).isEqualTo(20);
  }

  @Test
  void listarInvoices_comStatus_filtraPorFiscalStatus() {
    Page<NfseInvoiceEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
    when(nfseInvoiceRepository.pageByTenantAndOptionalStatus(tenantId, NfseFiscalStatus.AUTHORIZED, 2, 10))
        .thenReturn(page);

    NfseDtos.InvoiceListResponse response = service.listarInvoices("authorized", 2, 10);

    assertThat(response.total).isZero();
    assertThat(response.page).isEqualTo(2);
  }

  // ---------------------------------------------------------------- consultarTomadorPorCnpj

  @Test
  void consultarTomadorPorCnpj_cnpjInvalido_lancaExcecao() {
    assertThatThrownBy(() -> service.consultarTomadorPorCnpj("123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_TOMADOR_CNPJ_INVALID");
  }

  @Test
  void consultarTomadorPorCnpj_desabilitado_lancaExcecao() {
    NfseService disabledService =
        new NfseService(
            contextoTenant,
            authenticatedUser,
            meterRegistry,
            nfseConfigRepository,
            nfseInvoiceRepository,
            nfseInvoiceEventRepository,
            nfseInvoiceItemRepository,
            nfseProviderCapabilitiesRepository,
            fiscalCertificateService,
            nfseCertificateUnlockService,
            nfseProviderRouterService,
            nfseFiscalStateMachine,
            nfseXmlBuilderService,
            nfseXmlSignerService,
            encryptionService,
            brasilApiCnpjClient,
            nfseConfigService,
            false,
            true);

    assertThatThrownBy(() -> disabledService.consultarTomadorPorCnpj("11222333000181"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_TOMADOR_LOOKUP_DISABLED");
  }

  @Test
  void consultarTomadorPorCnpj_falhaNoClient_lancaIndisponivel() {
    when(brasilApiCnpjClient.lookup("11222333000181")).thenThrow(new RuntimeException("timeout"));

    assertThatThrownBy(() -> service.consultarTomadorPorCnpj("11.222.333/0001-81"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NFSE_TOMADOR_LOOKUP_UNAVAILABLE");
  }

  @Test
  void consultarTomadorPorCnpj_sucesso_mapeiaResposta() {
    BrasilApiCnpjClient.BrasilApiCnpjResponse response = new BrasilApiCnpjClient.BrasilApiCnpjResponse();
    response.razao_social = "Empresa Teste LTDA";
    response.nome_fantasia = "Teste";
    response.email = "contato@teste.com";
    response.ddd_telefone_1 = "11999998888";
    response.descricao_situacao_cadastral = "ATIVA";
    response.descricao_tipo_de_logradouro = "RUA";
    response.logradouro = "das Flores";
    response.numero = "100";
    response.bairro = "Centro";
    response.municipio = "Sao Paulo";
    response.uf = "SP";
    response.cep = "01000-000";
    when(brasilApiCnpjClient.lookup("11222333000181")).thenReturn(response);

    NfseDtos.TomadorLookupResponse dto = service.consultarTomadorPorCnpj("11222333000181");

    assertThat(dto.name).isEqualTo("Empresa Teste LTDA");
    assertThat(dto.active).isTrue();
    assertThat(dto.address.street).isEqualTo("RUA das Flores");
    assertThat(dto.address.zipCode).isEqualTo("01000000");
  }

  // ---------------------------------------------------------------- exportacaoContabil

  @Test
  void exportacaoContabil_periodoInvalido_lancaExcecao() {
    assertThatThrownBy(() -> service.exportacaoContabil("2026-02-01", "2026-01-01", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Periodo invalido");
  }

  @Test
  void exportacaoContabil_periodoMaiorQue365Dias_lancaExcecao() {
    assertThatThrownBy(() -> service.exportacaoContabil("2020-01-01", "2022-01-01", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("365 dias");
  }

  @Test
  void exportacaoContabil_csv_geraArquivoComCabecalho() {
    UUID invoiceId = UUID.randomUUID();
    NfseInvoiceEntity entity = draftEntity(invoiceId);
    entity.setFiscalStatus(NfseFiscalStatus.AUTHORIZED);
    when(nfseInvoiceRepository.listForAccountingExport(
            eq(tenantId), eq(LocalDate.parse("2026-01-01")), eq(LocalDate.parse("2026-01-31")), any()))
        .thenReturn(List.of(entity));

    NfseService.AccountingExportFile export = service.exportacaoContabil("2026-01-01", "2026-01-31", null, "CSV");

    assertThat(export.mediaType()).isEqualTo("text/csv");
    assertThat(export.rowsCount()).isEqualTo(1);
    String content = new String(export.content());
    assertThat(content).startsWith("invoice_id,tenant_id,");
    assertThat(content).contains(entity.getId().toString());
  }

  // ---------------------------------------------------------------- helpers

  private NfseConfigEntity config(String municipio, String provedor) {
    NfseConfigEntity config = new NfseConfigEntity();
    config.setMunicipioCodigoIbge(municipio);
    config.setProvedor(provedor);
    return config;
  }

  private NfseDtos.Invoice invoiceRequest() {
    NfseDtos.Invoice request = new NfseDtos.Invoice();
    request.ambiente = "HOMOLOGACAO";
    request.municipioCodigoIbge = "3550308";
    request.serieRps = "1";
    request.naturezaOperacao = "1";
    request.itemListaServico = "1.01";
    request.aliquotaIss = new BigDecimal("5.00");
    request.dataCompetencia = LocalDate.now().toString();

    NfseDtos.Customer customer = new NfseDtos.Customer();
    customer.type = "CPF";
    customer.document = "12345678900";
    customer.name = "Cliente Teste";
    request.customer = customer;

    NfseDtos.Item item = new NfseDtos.Item();
    item.lineNumber = 1;
    item.descricaoServico = "Corte de cabelo";
    item.quantidade = BigDecimal.ONE;
    item.valorUnitario = new BigDecimal("50.00");
    item.valorTotal = new BigDecimal("50.00");
    item.itemListaServico = "1.01";
    request.items = List.of(item);

    return request;
  }

  private NfseInvoiceEntity draftEntity(UUID id) {
    NfseInvoiceEntity entity = new NfseInvoiceEntity();
    entity.setId(id);
    entity.setTenantId(tenantId);
    entity.setCustomerType(NfseCustomerType.CPF);
    entity.setCustomerName("Cliente Teste");
    entity.setFiscalStatus(NfseFiscalStatus.DRAFT);
    entity.setMunicipioCodigoIbge("3550308");
    entity.setProvedor("ABRASF");
    entity.setAmbiente(NfseAmbiente.HOMOLOGACAO);
    entity.setNumeroRps(1L);
    entity.setSerieRps("1");
    entity.setDataCompetencia(LocalDate.now());
    entity.setNaturezaOperacao("1");
    entity.setItemListaServico("1.01");
    entity.setValorServicos(new BigDecimal("50.00"));
    entity.setValorDeducoes(BigDecimal.ZERO);
    entity.setValorIss(new BigDecimal("2.50"));
    entity.setAliquotaIss(new BigDecimal("5.00"));
    return entity;
  }

  private NfseInvoiceItemEntity itemEntity(UUID invoiceId) {
    NfseInvoiceItemEntity item = new NfseInvoiceItemEntity();
    item.setInvoiceId(invoiceId);
    item.setLineNumber(1);
    item.setDescricaoServico("Corte de cabelo");
    item.setQuantidade(BigDecimal.ONE);
    item.setValorUnitario(new BigDecimal("50.00"));
    item.setValorTotal(new BigDecimal("50.00"));
    item.setItemListaServico("1.01");
    item.setAliquotaIss(new BigDecimal("5.00"));
    item.setValorIss(new BigDecimal("2.50"));
    return item;
  }
}
