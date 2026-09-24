package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
  private ServicoTemplatesDoWhatsapp servicoTemplates;

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
    servicoTemplates = mock(ServicoTemplatesDoWhatsapp.class);
    when(servicoTemplates.templateParaTeste(any())).thenReturn(java.util.Optional.empty());

    when(contextoTenant.obterTenantIdOuFalhar()).thenReturn(tenantId);
    when(webhookVerifyTokenHashService.hash(anyString())).thenReturn("hashed-token");
    when(encryptionService.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
    when(encryptionService.decrypt(anyString()))
        .thenAnswer(inv -> ((String) inv.getArgument(0)).replaceFirst("^enc:", ""));
    when(repository.save(any(TenantWhatsAppConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    serviceEmbeddedHabilitado = new ServicoTenantWhatsapp(
        contextoTenant, auditService, repository, encryptionService, webhookVerifyTokenHashService,
        whatsAppClient, metaEmbeddedSignupClient, messageLogRepository, servicoTemplates, true, "teste_integracao", "pt_BR");
    serviceEmbeddedDesabilitado = new ServicoTenantWhatsapp(
        contextoTenant, auditService, repository, encryptionService, webhookVerifyTokenHashService,
        whatsAppClient, metaEmbeddedSignupClient, messageLogRepository, servicoTemplates, false, "teste_integracao", "pt_BR");
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

  /**
   * O erro que fez o dono perseguir o problema errado em 20/09: a Meta recusou com
   * {@code (#133010) Account not registered} — numero nao registrado no Cloud API — e a tela
   * mandou revisar credenciais que estavam corretas.
   */
  @Test
  void numeroNaoRegistradoNoCloudApiNaoManda_revisar_credenciais() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("(#133010) Account not registered"));

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    TenantWhatsAppDtos.TestMessageResponse response =
        serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(response.success).isFalse();
    assertThat(response.message).contains("registrado no WhatsApp Cloud API");
    assertThat(response.message).doesNotContain("Revise as credenciais");
    // O codigo da Meta e o que se pesquisa e o que se manda para o suporte dela.
    assertThat(response.message).contains("133010");
  }

  /**
   * A secao "Mensagens enviadas" da tela existia sem nunca receber dado: NADA no backend escrevia
   * em {@code whatsapp_message_log}. O erro vivia so num aviso que desaparece.
   */
  @Test
  void falhaNoEnvioDeTesteFicaRegistradaNoLogComOErroCruDaMeta() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("(#133010) Account not registered"));

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    ArgumentCaptor<WhatsAppMessageLogEntity> captor =
        ArgumentCaptor.forClass(WhatsAppMessageLogEntity.class);
    verify(messageLogRepository).save(captor.capture());
    WhatsAppMessageLogEntity gravada = captor.getValue();
    assertThat(gravada.getStatus()).isEqualTo("FAILED");
    assertThat(gravada.getEventType()).isEqualTo("TEST_MESSAGE");
    assertThat(gravada.getErrorMessage()).isEqualTo("(#133010) Account not registered");
  }

  @Test
  void envioDeTesteBemSucedidoFicaRegistradoNoLog() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.999");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    ArgumentCaptor<WhatsAppMessageLogEntity> captor =
        ArgumentCaptor.forClass(WhatsAppMessageLogEntity.class);
    verify(messageLogRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
    assertThat(captor.getValue().getProviderMessageId()).isEqualTo("wamid.999");
    assertThat(captor.getValue().getErrorMessage()).isNull();
  }

  /** Registrar e consequencia: se o insert falhar, o envio continua valendo. */
  @Test
  void falhaAoGravarNoLogNaoDerrubaOEnvio() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.777");
    when(messageLogRepository.save(any(WhatsAppMessageLogEntity.class)))
        .thenThrow(new RuntimeException("banco fora"));

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    TenantWhatsAppDtos.TestMessageResponse response =
        serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(response.success).isTrue();
    assertThat(response.providerMessageId).isEqualTo("wamid.777");
  }

  /** Erro que ninguem previu se explica na TELA: sem isso, so o log do servidor sabe o motivo. */
  @Test
  void erroDesconhecidoDaMetaChegaNaTelaComOTextoDela() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("(#131030) Recipient phone number not in allowed list"));

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    TenantWhatsAppDtos.TestMessageResponse response =
        serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(response.success).isFalse();
    assertThat(response.message).contains("131030").contains("not in allowed list");
  }

  @Test
  void enviarMensagemTesteComSucessoRegistraAuditoria() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
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

  /**
   * Registrar o numero e o que estava faltando: sem isso ele fica verificado e MUDO, recusando
   * todo envio com "(#133010) Account not registered" — visto em producao no numero do Azzo, com
   * as permissoes da Meta aprovadas desde julho.
   */
  @Test
  void registrarNumeroDestravaOEnvioEInscreveOWebhook() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString())).thenReturn(true);
    when(whatsAppClient.inscreverNoWebhook(any(TenantWhatsAppConfig.class))).thenReturn(true);

    TenantWhatsAppDtos.RegistroDoNumeroResponse response = serviceEmbeddedHabilitado.registrarNumero();

    assertThat(response.success).isTrue();
    assertThat(response.webhookInscrito).isTrue();
    assertThat(config.getWhatsappRegisteredAt()).isNotNull();
    // O dono precisa do PIN para levar o numero para outro provedor; esconder seria prende-lo.
    assertThat(response.registrationPin).hasSize(6).containsOnlyDigits();
    verify(auditService).recordSuccess(any(AuditEventCommand.class));
  }

  /** Ficar mudo e pior que nao receber: registrar sozinho ja vale, mas o retorno precisa dizer. */
  @Test
  void webhookQueFalhaNaoAnulaORegistro() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString())).thenReturn(true);
    when(whatsAppClient.inscreverNoWebhook(any(TenantWhatsAppConfig.class)))
        .thenThrow(new IllegalStateException("(#131009) Parameter value is not valid"));

    TenantWhatsAppDtos.RegistroDoNumeroResponse response = serviceEmbeddedHabilitado.registrarNumero();

    assertThat(response.success).isTrue();
    assertThat(response.webhookInscrito).isFalse();
    assertThat(response.message).contains("ainda nao RECEBE");
    assertThat(config.getWhatsappRegisteredAt()).isNotNull();
  }

  /** O PIN e sorteado uma vez e REAPROVEITADO: um PIN novo nao substitui o antigo sem ele. */
  @Test
  void oPinEReaproveitadoEntreRegistros() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString())).thenReturn(true);
    when(whatsAppClient.inscreverNoWebhook(any(TenantWhatsAppConfig.class))).thenReturn(true);

    String primeiro = serviceEmbeddedHabilitado.registrarNumero().registrationPin;
    String segundo = serviceEmbeddedHabilitado.registrarNumero().registrationPin;

    assertThat(segundo).isEqualTo(primeiro);
  }

  @Test
  void registroRecusadoPelaMetaNaoMarcaONumeroComoRegistrado() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString()))
        .thenThrow(new IllegalStateException("(#133010) Account not registered"));

    TenantWhatsAppDtos.RegistroDoNumeroResponse response = serviceEmbeddedHabilitado.registrarNumero();

    assertThat(response.success).isFalse();
    assertThat(config.getWhatsappRegisteredAt()).isNull();
  }

  /** Uma configuracao manual valida, com o numero que a Meta confirma. */
  private TenantWhatsAppDtos.UpdateRequest configuracaoValida() {
    WhatsAppClient.PhoneNumberDetails details = new WhatsAppClient.PhoneNumberDetails();
    details.id = "1234567890";
    details.displayPhoneNumber = "+55 11 99999-8888";
    details.verifiedName = "Meu Salao";
    when(whatsAppClient.fetchPhoneNumberDetails("token-abc", "1234567890")).thenReturn(details);

    TenantWhatsAppDtos.UpdateRequest request = new TenantWhatsAppDtos.UpdateRequest();
    request.accessToken = "token-abc";
    request.phoneNumberId = "1234567890";
    request.whatsappEnabled = true;
    return request;
  }

  /**
   * O cliente nao deve precisar saber que "registrar no Cloud API" existe: salvar a configuracao
   * tem que deixar o numero pronto para enviar.
   */
  @Test
  void salvarAConfiguracaoRegistraONumeroSozinho() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString())).thenReturn(true);
    when(whatsAppClient.inscreverNoWebhook(any(TenantWhatsAppConfig.class))).thenReturn(true);

    serviceEmbeddedHabilitado.atualizar(configuracaoValida());

    verify(whatsAppClient).registrarNumero(any(TenantWhatsAppConfig.class), anyString());
    verify(whatsAppClient).inscreverNoWebhook(any(TenantWhatsAppConfig.class));
    assertThat(config.getWhatsappRegisteredAt()).isNotNull();
  }

  /** Credencial valida guardada vale mais que nada: o botao da tela cobre o registro depois. */
  @Test
  void registroQueFalhaNaoDerrubaOSalvamentoDaConfiguracao() {
    TenantWhatsAppConfig config = configVazia();
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString()))
        .thenThrow(new IllegalStateException("(#133010) Account not registered"));

    TenantWhatsAppDtos.ConfigResponse response =
        serviceEmbeddedHabilitado.atualizar(configuracaoValida());

    assertThat(response.phoneNumberId).isNotBlank();
    // Nulo e o que mantem o aviso e o botao visiveis na tela.
    assertThat(config.getWhatsappRegisteredAt()).isNull();
    assertThat(config.getEmbeddedSignupLastError()).contains("133010");
  }

  /** Registrar de novo a cada salvamento seria bater na Meta sem motivo. */
  @Test
  void configuracaoJaRegistradaNaoRegistraDeNovo() {
    TenantWhatsAppConfig config = configVazia();
    config.setWhatsappRegisteredAt(java.time.Instant.now());
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    serviceEmbeddedHabilitado.atualizar(configuracaoValida());

    verify(whatsAppClient, never()).registrarNumero(any(TenantWhatsAppConfig.class), anyString());
  }

  /**
   * Texto livre so chega dentro de 24h da ultima mensagem do CLIENTE. O teste quase sempre e
   * primeiro contato, e nesse caso a Meta aceita, devolve wamid e descarta — em 22/09/2026 foram
   * tres envios com wamid valido, nenhum entregue, e a tela dizendo "Entregue".
   */
  @Test
  void oTestePadraoMandaTemplate() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.tpl");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    TenantWhatsAppDtos.TestMessageResponse response =
        serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(response.success).isTrue();
    verify(whatsAppClient)
        .enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), eq("teste_integracao"), eq("pt_BR"), anyList());
    verify(whatsAppClient, never()).sendMessage(any(TenantWhatsAppConfig.class), anyString(), anyString());
    // "Aceita", e nao "entregue": o wamid prova que a Meta aceitou, nao que alguem recebeu.
    assertThat(response.message).contains("aceito pela Meta").doesNotContain("entregue");
  }

  /** Dentro da janela, texto livre funciona — e e o que o atendimento de verdade usa. */
  @Test
  void textoLivreContinuaDisponivelQuandoPedidoExplicitamente() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.sendMessage(any(TenantWhatsAppConfig.class), anyString(), anyString()))
        .thenReturn("wamid.txt");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";
    request.message = "oi";

    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    verify(whatsAppClient).sendMessage(any(TenantWhatsAppConfig.class), anyString(), eq("oi"));
    verify(whatsAppClient, never())
        .enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void oTemplateEscolhidoPeloSalaoVenceOPadrao() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.tpl");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";
    request.templateName = "confirmacao_agendamento";
    request.templateLanguage = "pt_BR";

    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    verify(whatsAppClient)
        .enviarTemplate(
            any(TenantWhatsAppConfig.class), anyString(), eq("confirmacao_agendamento"), eq("pt_BR"),
            anyList());
  }

  /** O log precisa dizer QUAL template foi mandado: "Entregue" sozinho ja enganou uma vez. */
  @Test
  void oLogRegistraQualTemplateFoiEnviado() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.tpl");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    ArgumentCaptor<WhatsAppMessageLogEntity> captor =
        ArgumentCaptor.forClass(WhatsAppMessageLogEntity.class);
    verify(messageLogRepository).save(captor.capture());
    assertThat(captor.getValue().getMessageText()).contains("teste_integracao").contains("pt_BR");
  }

  /**
   * O template do PROPRIO salao vence o do ambiente: `teste_integracao` e criado na conta dele e
   * e o que prova a integracao DELE. O valor de ambiente virou reserva.
   */
  @Test
  void oTesteUsaOTemplateDoProprioSalao() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(servicoTemplates.templateParaTeste(any()))
        .thenReturn(
            java.util.Optional.of(
                new ServicoTemplatesDoWhatsapp.TemplateParaTeste(
                    "teste_integracao", "pt_BR", java.util.List.of(), true)));
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.tpl");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    TenantWhatsAppDtos.TestMessageResponse response =
        serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(response.success).isTrue();
    verify(whatsAppClient)
        .enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), eq("teste_integracao"), eq("pt_BR"), anyList());
  }

  /** Recusa por analise pendente nao pode ser confundida com credencial errada. */
  @Test
  void templateNaoAprovadoAvisaQueAAnaliseEstaPendente() {
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(configVazia());
    when(servicoTemplates.templateParaTeste(any()))
        .thenReturn(
            java.util.Optional.of(
                new ServicoTemplatesDoWhatsapp.TemplateParaTeste(
                    "teste_integracao", "pt_BR", java.util.List.of(), false)));
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenReturn("wamid.tpl");

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";

    assertThat(serviceEmbeddedHabilitado.enviarMensagemTeste(request).message)
        .contains("ainda nao foi aprovado");
  }

  /**
   * O beco sem saida de 2026-09-23: o registro era gravado como flag da CONFIGURACAO, e nao do
   * numero. Trocar de numero deixava a flag marcada pelo antigo — o registro automatico pulava, e
   * o aviso e o botao da tela ficavam escondidos pelo mesmo motivo.
   */
  @Test
  void trocarDeNumeroObrigaARegistrarDeNovo() {
    TenantWhatsAppConfig config = configVazia();
    config.setWhatsappPhoneNumberId("numero-antigo");
    config.setWhatsappRegisteredAt(java.time.Instant.now());
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.registrarNumero(any(TenantWhatsAppConfig.class), anyString())).thenReturn(true);
    when(whatsAppClient.inscreverNoWebhook(any(TenantWhatsAppConfig.class))).thenReturn(true);

    serviceEmbeddedHabilitado.atualizar(configuracaoValida());

    // O numero novo passou pelo registro em vez de herdar a marca do anterior.
    verify(whatsAppClient).registrarNumero(any(TenantWhatsAppConfig.class), anyString());
  }

  @Test
  void manterOMesmoNumeroNaoRegistraDeNovo() {
    TenantWhatsAppConfig config = configVazia();
    config.setWhatsappPhoneNumberId("1234567890");
    config.setWhatsappRegisteredAt(java.time.Instant.now());
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);

    serviceEmbeddedHabilitado.atualizar(configuracaoValida());

    verify(whatsAppClient, never()).registrarNumero(any(TenantWhatsAppConfig.class), anyString());
  }

  /** Confiar na flag contra a resposta da Meta e o que criava o beco sem saida. */
  @Test
  void metaDizendoNaoRegistradoDesmarcaAFlag() {
    TenantWhatsAppConfig config = configVazia();
    config.setWhatsappRegisteredAt(java.time.Instant.now());
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("(#133010) Account not registered"));

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";
    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    // Nulo e o que faz o aviso e o botao "Registrar numero" voltarem para a tela.
    assertThat(config.getWhatsappRegisteredAt()).isNull();
  }

  /** Erro de outra natureza nao pode apagar um registro que esta correto. */
  @Test
  void outroErroDaMetaNaoDesmarcaORegistro() {
    TenantWhatsAppConfig config = configVazia();
    java.time.Instant registradoEm = java.time.Instant.now();
    config.setWhatsappRegisteredAt(registradoEm);
    when(repository.findByTenantIdOrCreate(tenantId)).thenReturn(config);
    when(whatsAppClient.enviarTemplate(any(TenantWhatsAppConfig.class), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("(#131037) needs display name approval"));

    TenantWhatsAppDtos.TestMessageRequest request = new TenantWhatsAppDtos.TestMessageRequest();
    request.destinationPhone = "+5511999998888";
    serviceEmbeddedHabilitado.enviarMensagemTeste(request);

    assertThat(config.getWhatsappRegisteredAt()).isEqualTo(registradoEm);
  }
}
