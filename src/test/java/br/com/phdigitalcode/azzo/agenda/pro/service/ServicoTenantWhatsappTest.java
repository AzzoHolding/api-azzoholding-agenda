package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import br.com.phdigitalcode.azzo.agenda.pro.dto.TenantWhatsAppDtos;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.MetaEmbeddedSignupGateway;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;

/** Cobre {@code ServicoTenantWhatsapp}: espelha o Quarkus original, incluindo Embedded Signup. */
class ServicoTenantWhatsappTest {

  private final UUID tenantId = UUID.randomUUID();

  private ContextoTenant contextoTenant;
  private AuditService auditService;
  private TenantWhatsAppConfigRepository repository;
  private EncryptionService encryptionService;
  private WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private WhatsAppClient whatsAppClient;
  private MetaEmbeddedSignupGateway metaEmbeddedSignupClient;
  private WhatsAppMessageLogRepository messageLogRepository;

  private ServicoTenantWhatsapp serviceEmbeddedHabilitado;
  private ServicoTenantWhatsapp serviceEmbeddedDesabilitado;

  @BeforeEach
  void setUp() {
    contextoTenant = mock(ContextoTenant.class);
    auditService = mock(AuditService.class);
    repository = mock(TenantWhatsAppConfigRepository.class);
    encryptionService = mock(EncryptionService.class);
    webhookVerifyTokenHashService = mock(WebhookVerifyTokenHashService.class);
    whatsAppClient = mock(WhatsAppClient.class);
    metaEmbeddedSignupClient = mock(MetaEmbeddedSignupGateway.class);
    messageLogRepository = mock(WhatsAppMessageLogRepository.class);

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(webhookVerifyTokenHashService.hash(anyString())).thenReturn("hashed-token");
    when(encryptionService.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
    when(encryptionService.decrypt(anyString()))
        .thenAnswer(inv -> ((String) inv.getArgument(0)).replaceFirst("^enc:", ""));
    when(repository.save(any(TenantWhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    serviceEmbeddedHabilitado = new ServicoTenantWhatsapp(
        contextoTenant, auditService, repository, encryptionService, webhookVerifyTokenHashService,
        whatsAppClient, metaEmbeddedSignupClient, messageLogRepository, true);
    serviceEmbeddedDesabilitado = new ServicoTenantWhatsapp(
        contextoTenant, auditService, repository, encryptionService, webhookVerifyTokenHashService,
        whatsAppClient, metaEmbeddedSignupClient, messageLogRepository, false);
  }

  private TenantWhatsAppConfig configVazia() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappAccessTokenEnc("");
    config.setWhatsappEnabled(false);
    config.setCanSchedule(true);
    config.setCanCancel(true);
    config.setCanReschedule(true);
    return config;
  }

  @Test
  void atualizarSemTokenNaPrimeiraConfiguracaoLancaExcecao() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());

    TenantWhatsAppDtos.UpdateRequest request = new TenantWhatsAppDtos.UpdateRequest();
    request.whatsappEnabled = true;

    assertThatThrownBy(() -> serviceEmbeddedHabilitado.atualizar(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("obrigatorio na primeira configuracao");
  }

  @Test
  void atualizarComTokenEPhoneNumberIdValidosMarcaOnboardingConectado() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    WhatsAppClient.PhoneNumberDetails details = new WhatsAppClient.PhoneNumberDetails();
    details.id = "1234567890";
    details.displayPhoneNumber = "+55 11 99999-8888";
    details.verifiedName = "Meu Salao";
    when(whatsAppClient.fetchPhoneNumberDetails("token-abc", "1234567890")).thenReturn(details);

    TenantWhatsAppDtos.UpdateRequest request = new TenantWhatsAppDtos.UpdateRequest();
    request.accessToken = "token-abc";
    request.phoneNumberId = "1234567890";
    request.whatsappEnabled = true;

    TenantWhatsAppDtos.ConfigResponse response = serviceEmbeddedHabilitado.atualizar(request);

    assertThat(response.onboardingStatus).isEqualTo("CONNECTED");
    assertThat(response.accessTokenConfigured).isTrue();
    assertThat(response.displayPhoneNumber).isEqualTo("+55 11 99999-8888");
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void atualizarComPerfilReactiveOnlyDesativaAcoesProativas() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());

    TenantWhatsAppDtos.UpdateRequest request = new TenantWhatsAppDtos.UpdateRequest();
    request.accessToken = "token-abc";
    request.whatsappEnabled = true;
    request.usageProfile = "REACTIVE_ONLY";

    TenantWhatsAppDtos.ConfigResponse response = serviceEmbeddedHabilitado.atualizar(request);

    assertThat(response.usageProfile).isEqualTo("REACTIVE_ONLY");
    assertThat(response.canSchedule).isFalse();
    assertThat(response.canCancel).isFalse();
    assertThat(response.canReschedule).isFalse();
  }

  @Test
  void atualizarPreferenciasAplicaPatchLeveSemExigirToken() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    TenantWhatsAppDtos.SettingsPatchRequest request = new TenantWhatsAppDtos.SettingsPatchRequest();
    request.whatsappEnabled = true;
    request.canCancel = false;

    TenantWhatsAppDtos.ConfigResponse response = serviceEmbeddedHabilitado.atualizarPreferencias(request);

    assertThat(response.whatsappEnabled).isTrue();
    assertThat(response.canCancel).isFalse();
    assertThat(response.canSchedule).isTrue();
  }

  @Test
  void testarConexaoComFalhaRetornaMensagemMapeada() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.testConnection(any(TenantWhatsAppConfig.class)))
        .thenThrow(new IllegalArgumentException("Token do WhatsApp nao configurado para o tenant"));

    TenantWhatsAppDtos.TestResponse response = serviceEmbeddedHabilitado.testarConexao();

    assertThat(response.success).isFalse();
    assertThat(response.message).contains("Salve a configuracao antes de testar");
    verify(auditService).recordError(any(AuditEventCommand.class));
  }

  @Test
  void enviarMensagemTesteComSucessoRegistraAuditoria() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.sendMessage(any(TenantWhatsAppConfig.class), anyString(), anyString()))
        .thenReturn("wamid.123");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    TenantWhatsAppDtos.TestMessageResponse response = serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(response.success).isTrue();
    assertThat(response.providerMessageId).isEqualTo("wamid.123");
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void concluirEmbeddedSignupDesabilitadoRetornaConnectedFalse() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());

    TenantWhatsAppDtos.EmbeddedSignupCompleteRequest request = new TenantWhatsAppDtos.EmbeddedSignupCompleteRequest();
    request.code = "code-123";
    request.setupInfo = new TenantWhatsAppDtos.SetupInfo();
    request.setupInfo.wabaId = "waba-1";
    request.setupInfo.phoneNumberId = "1234567890";

    TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = serviceEmbeddedDesabilitado.concluirEmbeddedSignup(request);

    assertThat(response.connected).isFalse();
    assertThat(response.lastError).isEqualTo("Embedded Signup desabilitado neste ambiente.");
    verify(metaEmbeddedSignupClient, never()).exchangeCodeForAccessToken(anyString());
  }

  @Test
  void concluirEmbeddedSignupComSucessoConectaEHabilitaWhatsapp() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(metaEmbeddedSignupClient.exchangeCodeForAccessToken("code-123")).thenReturn("token-embedded");
    MetaEmbeddedSignupClient.PhoneNumberDetails phoneDetails = new MetaEmbeddedSignupClient.PhoneNumberDetails();
    phoneDetails.id = "1234567890";
    phoneDetails.displayPhoneNumber = "+55 11 99999-8888";
    when(metaEmbeddedSignupClient.fetchPhoneNumberDetails("token-embedded", "1234567890")).thenReturn(phoneDetails);
    when(whatsAppClient.testConnection(any(TenantWhatsAppConfig.class))).thenReturn(true);

    TenantWhatsAppDtos.EmbeddedSignupCompleteRequest request = new TenantWhatsAppDtos.EmbeddedSignupCompleteRequest();
    request.code = "code-123";
    request.setupInfo = new TenantWhatsAppDtos.SetupInfo();
    request.setupInfo.wabaId = "waba-1";
    request.setupInfo.phoneNumberId = "1234567890";

    TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = serviceEmbeddedHabilitado.concluirEmbeddedSignup(request);

    assertThat(response.connected).isTrue();
    assertThat(response.onboardingStatus).isEqualTo("CONNECTED");
    assertThat(response.tokenSource).isEqualTo("EMBEDDED_CODE_EXCHANGE");
    assertThat(response.whatsappEnabled).isTrue();
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  @Test
  void concluirEmbeddedSignupComFalhaNaTrocaDeCodeMarcaFailed() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(metaEmbeddedSignupClient.exchangeCodeForAccessToken("code-invalido"))
        .thenThrow(new IllegalStateException("Code invalido ou expirado"));

    TenantWhatsAppDtos.EmbeddedSignupCompleteRequest request = new TenantWhatsAppDtos.EmbeddedSignupCompleteRequest();
    request.code = "code-invalido";
    request.setupInfo = new TenantWhatsAppDtos.SetupInfo();
    request.setupInfo.wabaId = "waba-1";
    request.setupInfo.phoneNumberId = "1234567890";

    TenantWhatsAppDtos.EmbeddedSignupStatusResponse response = serviceEmbeddedHabilitado.concluirEmbeddedSignup(request);

    assertThat(response.connected).isFalse();
    assertThat(response.onboardingStatus).isEqualTo("FAILED");
    assertThat(response.whatsappEnabled).isFalse();
    assertThat(response.lastError).isEqualTo("Code invalido ou expirado");
    verify(auditService).recordError(any(AuditEventCommand.class));
  }

  @Test
  void listarMensagensRetornaHasMoreQuandoExcedeLimite() {
    WhatsAppMessageLogEntity m1 = new WhatsAppMessageLogEntity();
    m1.setId(UUID.randomUUID());
    m1.setTenantId(tenantId);
    m1.setEventType("CONFIRMATION");
    m1.setDestinationPhone("5511999998888");
    m1.setSentAt(Instant.now());
    WhatsAppMessageLogEntity m2 = new WhatsAppMessageLogEntity();
    m2.setId(UUID.randomUUID());
    m2.setTenantId(tenantId);
    m2.setEventType("REMINDER");
    m2.setDestinationPhone("5511999997777");
    m2.setSentAt(Instant.now().minusSeconds(60));

    when(messageLogRepository.findByTenantIdOrderBySentAtDesc(org.mockito.ArgumentMatchers.eq(tenantId), any(Pageable.class)))
        .thenReturn(List.of(m1, m2));

    TenantWhatsAppDtos.MessageLogResponse response = serviceEmbeddedHabilitado.listarMensagens(1);

    assertThat(response.items).hasSize(1);
    assertThat(response.items.get(0).eventType).isEqualTo("CONFIRMATION");
    assertThat(response.hasMore).isTrue();
    assertThat(response.nextCursorSentAt).isNotNull();
  }
}
