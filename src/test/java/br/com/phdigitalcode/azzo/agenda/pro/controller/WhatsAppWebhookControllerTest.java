package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationConsentHistoryEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationConsentHistoryRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatMessageStatusService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatService;
import br.com.phdigitalcode.azzo.agenda.pro.service.LicenseStatusService;
import br.com.phdigitalcode.azzo.agenda.pro.service.NotificationPublisher;
import br.com.phdigitalcode.azzo.agenda.pro.service.WhatsAppBookingReactivationService;

/**
 * Espelha o contrato de {@code modules/chat/api/webhook/WhatsAppWebhookResource.java}: assinatura
 * HMAC, verificacao do webhook (GET), isolamento de falha por mensagem/status (comentarios "Fix 2"
 * do original) e o gate de licenca (comentario "Fix 4"). Segue o mesmo padrao de
 * {@link TelegramWebhookControllerTest}.
 */
class WhatsAppWebhookControllerTest {

  private final UUID tenantId = UUID.randomUUID();
  private final String phoneNumberId = "PHONE-123";

  private TenantRepository tenantRepository;
  private TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private NotificationPublisher notificationPublisher;
  private WhatsAppClient whatsAppClient;
  private WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private ChatMessageStatusService chatMessageStatusService;
  private ChatService chatService;
  private WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private AssistantApiClient assistantApiClient;
  private ReactivationConsentHistoryRepository consentHistoryRepository;
  private WhatsAppBookingReactivationCycleRepository reactivationCycleRepository;
  private ClienteRepository clienteRepository;
  private LicenseStatusService licenseStatusService;

  private Tenant tenant;
  private TenantWhatsAppConfig config;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    tenantWhatsAppConfigRepository = mock(TenantWhatsAppConfigRepository.class);
    notificationPublisher = mock(NotificationPublisher.class);
    whatsAppClient = mock(WhatsAppClient.class);
    webhookVerifyTokenHashService = mock(WebhookVerifyTokenHashService.class);
    chatMessageStatusService = mock(ChatMessageStatusService.class);
    chatService = mock(ChatService.class);
    whatsAppBookingReactivationService = mock(WhatsAppBookingReactivationService.class);
    assistantApiClient = mock(AssistantApiClient.class);
    consentHistoryRepository = mock(ReactivationConsentHistoryRepository.class);
    reactivationCycleRepository = mock(WhatsAppBookingReactivationCycleRepository.class);
    clienteRepository = mock(ClienteRepository.class);
    licenseStatusService = mock(LicenseStatusService.class);

    tenant = new Tenant();
    tenant.setId(tenantId);

    config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappEnabled(true);

    when(tenantWhatsAppConfigRepository.findByPhoneNumberId(phoneNumberId)).thenReturn(Optional.of(config));
    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(false);
    when(clienteRepository.findFirstByTenantIdAndPhone(any(), any())).thenReturn(Optional.empty());
    when(clienteRepository.findFirstByTenantIdAndPhoneContainingNormalizedRaw(any(), any())).thenReturn(Optional.empty());
  }

  // ---- helpers ----------------------------------------------------------

  private WhatsAppWebhookController controllerWithoutSecret() {
    return new WhatsAppWebhookController(
        tenantRepository,
        tenantWhatsAppConfigRepository,
        notificationPublisher,
        whatsAppClient,
        webhookVerifyTokenHashService,
        chatMessageStatusService,
        chatService,
        whatsAppBookingReactivationService,
        assistantApiClient,
        new ObjectMapper(),
        consentHistoryRepository,
        reactivationCycleRepository,
        clienteRepository,
        licenseStatusService,
        "__unset__",
        false,
        "test");
  }

  private WhatsAppWebhookController controllerWithSecret(String secret, String profile) {
    return new WhatsAppWebhookController(
        tenantRepository,
        tenantWhatsAppConfigRepository,
        notificationPublisher,
        whatsAppClient,
        webhookVerifyTokenHashService,
        chatMessageStatusService,
        chatService,
        whatsAppBookingReactivationService,
        assistantApiClient,
        new ObjectMapper(),
        consentHistoryRepository,
        reactivationCycleRepository,
        clienteRepository,
        licenseStatusService,
        secret,
        false,
        profile);
  }

  private String hmac(String secret, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] computed = mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(computed.length * 2);
    for (byte b : computed) sb.append(String.format("%02x", b));
    return "sha256=" + sb;
  }

  private String textMessagePayload(String wamid, String from, String text) {
    return "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"messages\":[{\"id\":\"" + wamid + "\",\"from\":\"" + from + "\",\"type\":\"text\","
        + "\"text\":{\"body\":\"" + text + "\"}}]"
        + "}}]}]}";
  }

  private ChatConversationEntity conversation() {
    ChatConversationEntity conv = new ChatConversationEntity();
    conv.setId(UUID.randomUUID());
    conv.setTenantId(tenantId);
    return conv;
  }

  private Cliente cliente() {
    Cliente cliente = new Cliente();
    cliente.setId(UUID.randomUUID());
    cliente.setTenantId(tenantId);
    cliente.setName("Joana");
    cliente.setPhone("5511999999999");
    return cliente;
  }

  // ---- GET verifyWebhook --------------------------------------------------

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(WhatsAppWebhookController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/webhook/whatsapp");
  }

  @Test
  void verificacaoComModoInvalidoEhBadRequest() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    ResponseEntity<String> response = controller.verifyWebhook("unsubscribe", "token", "challenge");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void verificacaoComTokenOuChallengeAusenteEhBadRequest() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    assertThat(controller.verifyWebhook("subscribe", null, "challenge").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(controller.verifyWebhook("subscribe", "token", null).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void verificacaoComTokenInvalidoEhForbidden() {
    when(webhookVerifyTokenHashService.hash("token-errado")).thenReturn("hash-diferente");
    when(tenantWhatsAppConfigRepository.existsByWebhookVerifyTokenHash("hash-diferente")).thenReturn(false);

    WhatsAppWebhookController controller = controllerWithoutSecret();
    ResponseEntity<String> response = controller.verifyWebhook("subscribe", "token-errado", "challenge-xyz");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void verificacaoComTokenValidoRetornaChallenge() {
    when(webhookVerifyTokenHashService.hash("token-certo")).thenReturn("hash-esperado");
    when(tenantWhatsAppConfigRepository.existsByWebhookVerifyTokenHash("hash-esperado")).thenReturn(true);

    WhatsAppWebhookController controller = controllerWithoutSecret();
    ResponseEntity<String> response = controller.verifyWebhook("subscribe", "token-certo", "challenge-xyz");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("challenge-xyz");
  }

  // ---- POST receive: assinatura HMAC --------------------------------------

  @Test
  void assinaturaInvalidaEhRejeitada() throws Exception {
    WhatsAppWebhookController controller = controllerWithSecret("meu-segredo", "prod");
    String body = "{}";
    String badSignature = "sha256=" + "0".repeat(64);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(badSignature, body);

    assertThat(response.status).isEqualTo("REJECTED");
    assertThat(response.message).isEqualTo("Assinatura invalida");
  }

  @Test
  void assinaturaValidaPassaEProcessaPayloadVazio() throws Exception {
    WhatsAppWebhookController controller = controllerWithSecret("meu-segredo", "prod");
    String body = "";
    String signature = hmac("meu-segredo", body);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(signature, body);

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Payload vazio");
  }

  @Test
  void segredoNaoConfiguradoEmProducaoRejeitaFailClosed() {
    WhatsAppWebhookController controller = controllerWithSecret("__unset__", "prod");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, "{}");

    assertThat(response.status).isEqualTo("REJECTED");
  }

  @Test
  void segredoNaoConfiguradoForaDeProducaoPermiteTrafego() {
    WhatsAppWebhookController controller = controllerWithoutSecret();

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, "{}");

    // payload "{}" nao tem "entry" -> ignorado, mas nao rejeitado por assinatura
    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Sem entradas");
  }

  // ---- POST receive: payload malformado / vazio / sem entradas -----------

  @Test
  void payloadJsonInvalidoEhIgnorado() {
    WhatsAppWebhookController controller = controllerWithoutSecret();

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, "{invalido");

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Payload invalido");
  }

  @Test
  void payloadNuloOuEmBrancoEhIgnorado() {
    WhatsAppWebhookController controller = controllerWithoutSecret();

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, null);

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Payload vazio");
  }

  @Test
  void payloadSemEntradasEhIgnorado() {
    WhatsAppWebhookController controller = controllerWithoutSecret();

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, "{\"foo\":\"bar\"}");

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Sem entradas");
  }

  // ---- POST receive: change ignorado (phone_number_id ausente / tenant) --

  @Test
  void changeSemPhoneNumberIdEhIgnorado() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{\"metadata\":{}}}]}]}";

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.skipped).isEqualTo(1);
    assertThat(response.details).contains("change ignorado: phone_number_id ausente");
  }

  @Test
  void changeComTenantNaoEncontradoEhIgnorado() {
    when(tenantWhatsAppConfigRepository.findByPhoneNumberId(phoneNumberId)).thenReturn(Optional.empty());
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.skipped).isEqualTo(1);
    assertThat(response.details.get(0)).contains("tenant nao encontrado/habilitado");
  }

  @Test
  void changeComWhatsappDesabilitadoEhIgnorado() {
    config.setWhatsappEnabled(false);
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.skipped).isEqualTo(1);
  }

  @Test
  void changeComTenantComLicencaSuspensaEhIgnorado() {
    when(licenseStatusService.deveBloquear(tenantId)).thenReturn(true);
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.skipped).isEqualTo(1);
    assertThat(response.details.get(0)).contains("plano suspenso/vencido");
    verify(chatService, never()).processInboundWhatsAppMessage(any(), any(), any(), any(), any());
  }

  // ---- POST receive: mensagens duplicadas ---------------------------------

  @Test
  void mensagemDuplicadaNoMesmoPayloadEhIgnorada() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    // mesma wamid duas vezes no mesmo payload
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"messages\":["
        + "{\"id\":\"wamid-dup\",\"from\":\"5511988887777\",\"type\":\"text\",\"text\":{\"body\":\"ola\"}},"
        + "{\"id\":\"wamid-dup\",\"from\":\"5511988887777\",\"type\":\"text\",\"text\":{\"body\":\"ola de novo\"}}"
        + "]}}]}]}";

    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conversation();
    inbound.client = cliente();
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any(AssistantMessageRequest.class)))
        .thenReturn(new AssistantMessageResponse("ok"));

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.skipped).isEqualTo(1);
    assertThat(response.details).anyMatch(d -> d.contains("mensagem duplicada ignorada no mesmo payload"));
    // apenas uma das duas mensagens deve ter sido processada
    verify(chatService, times(1)).processInboundWhatsAppMessage(any(), any(), any(), any(), any());
  }

  @Test
  void mensagemDuplicadaNoBancoEhIgnoradaSemChamarAssistant() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conversation();
    inbound.client = cliente();
    inbound.duplicateInboundMessage = true;
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.success).isEqualTo(1);
    verify(assistantApiClient, never()).processarMensagem(any(), any(), any(), any());
  }

  // ---- POST receive: from/body ausente ------------------------------------

  @Test
  void mensagemSemFromOuBodyEhIgnorada() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"messages\":[{\"id\":\"wamid-1\",\"type\":\"text\"}]"
        + "}}]}]}";

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.skipped).isEqualTo(1);
    assertThat(response.details).contains("mensagem ignorada: from/body ausente");
  }

  // ---- POST receive: fluxo feliz texto ------------------------------------

  @Test
  void fluxoFelizProcessaTextoEEnviaRespostaDoAssistant() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    ChatConversationEntity conv = conversation();
    Cliente cli = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cli;
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any(AssistantMessageRequest.class)))
        .thenReturn(new AssistantMessageResponse("Resposta do assistente"));
    when(whatsAppClient.sendMessage(any(), any(), any())).thenReturn("provider-msg-1");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.success).isEqualTo(1);
    assertThat(response.outboundMessages).hasSize(1);
    verify(whatsAppClient).sendMessage(eq(config), eq("5511988887777"), eq("Resposta do assistente"));
    verify(chatService).registerOutboundAssistantMessage(
        tenantId, conv.getId(), cli.getId(), "Resposta do assistente", "provider-msg-1", ChatMessageStatus.SENT, null, null);
    verify(whatsAppBookingReactivationService).syncAfterAssistantTurn(eq(tenantId), eq(conv), eq(cli), eq("ola"), any());
  }

  @Test
  void modoManualAtivoEhRegistradoSemChamarAssistant() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conversation();
    inbound.client = cliente();
    inbound.manualModeEnabled = true;
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.success).isEqualTo(1);
    verify(assistantApiClient, never()).processarMensagem(any(), any(), any(), any());
  }

  // ---- POST receive: falha do assistant / fallback ------------------------

  @Test
  void falhaDoAssistantEnviaFallbackERegistraNotificacaoDeFalhaDeEntrega() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    ChatConversationEntity conv = conversation();
    Cliente cli = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cli;
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("assistant indisponivel"));
    when(whatsAppClient.sendMessage(any(), any(), any())).thenReturn("provider-fallback-1");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("ERROR");
    assertThat(response.failed).isEqualTo(1);
    verify(whatsAppClient).sendMessage(eq(config), eq("5511988887777"),
        eq("Nao foi possivel processar sua mensagem agora. Tente novamente em instantes."));
    verify(notificationPublisher).publish(
        eq(tenantId), eq((UUID) null), eq("WHATSAPP_DELIVERY_ERROR"), eq("5511988887777"),
        anyString(), eq(StatusNotification.FAILED), anyString(), eq((java.time.Instant) null), eq(600L));
  }

  @Test
  void falhaDoAssistantPorConfiguracaoRegistraNotificacaoDeConfiguracao() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    ChatConversationEntity conv = conversation();
    Cliente cli = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cli;
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("Token do WhatsApp nao configurado para o tenant"));
    when(whatsAppClient.sendMessage(any(), any(), any())).thenReturn("provider-fallback-1");

    controller.receive(null, payload);

    verify(notificationPublisher).publish(
        eq(tenantId), eq((UUID) null), eq("WHATSAPP_CONFIG_ALERT"), eq("5511988887777"),
        anyString(), eq(StatusNotification.FAILED), anyString(), eq((java.time.Instant) null), eq(1800L));
  }

  @Test
  void falhaAoEnviarFallbackNaoPropagaExcecao() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "ola");

    ChatConversationEntity conv = conversation();
    Cliente cli = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cli;
    when(chatService.processInboundWhatsAppMessage(any(), any(), any(), any(), any())).thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("assistant indisponivel"));
    when(whatsAppClient.sendMessage(any(), any(), any()))
        .thenThrow(new RuntimeException("falha no envio do fallback"));

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("ERROR");
    assertThat(response.failed).isEqualTo(1);
    verify(chatService, never()).registerOutboundAssistantMessage(any(), any(), any(), any(), any(), any(), any(), any());
  }

  // ---- POST receive: isolamento de falha (Fix 2) --------------------------

  @Test
  void excecaoInesperadaEmUmaMensagemNaoAbortaAsDemaisDoMesmoPayload() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"messages\":["
        + "{\"id\":\"wamid-1\",\"from\":\"5511988887777\",\"type\":\"text\",\"text\":{\"body\":\"ola\"}},"
        + "{\"id\":\"wamid-2\",\"from\":\"5511988886666\",\"type\":\"text\",\"text\":{\"body\":\"oi\"}}"
        + "]}}]}]}";

    ChatConversationEntity conv = conversation();
    Cliente cli = cliente();
    ChatService.InboundProcessingResult inboundOk = new ChatService.InboundProcessingResult();
    inboundOk.conversation = conv;
    inboundOk.client = cli;
    when(chatService.processInboundWhatsAppMessage(eq(tenantId), eq("5511988887777"), any(), any(), any()))
        .thenThrow(new IllegalStateException("erro totalmente inesperado"));
    when(chatService.processInboundWhatsAppMessage(eq(tenantId), eq("5511988886666"), any(), any(), any()))
        .thenReturn(inboundOk);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any()))
        .thenReturn(new AssistantMessageResponse("Resposta do assistente"));
    when(whatsAppClient.sendMessage(any(), any(), any())).thenReturn("provider-msg-2");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    // isolamento: a mensagem com falha nao aborta a transacao nem impede a segunda de ser
    // processada com sucesso; o resultado observavel e HTTP 200 sempre (nunca escapa excecao)
    assertThat(response.status).isEqualTo("PARTIAL");
    assertThat(response.processed).isEqualTo(2);
    assertThat(response.failed).isEqualTo(1);
    assertThat(response.success).isEqualTo(1);
    assertThat(response.details).anyMatch(d -> d.contains("falha isolada ao processar mensagem"));
  }

  @Test
  void statusMalformadoNaoAbortaOProcessamentoDosDemais() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"statuses\":["
        + "{\"id\":\"m1\",\"status\":\"delivered\"},"
        + "{\"id\":\"m2\",\"status\":\"read\"}"
        + "]}}]}]}";

    when(chatMessageStatusService.applyProviderStatus(eq(tenantId), eq("m1"), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("status corrompido"));
    when(chatMessageStatusService.applyProviderStatus(eq(tenantId), eq("m2"), any(), any(), any(), any()))
        .thenReturn(true);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.details).anyMatch(d -> d.contains("falha isolada ao processar status"));
    assertThat(response.details).anyMatch(d -> d.contains("status atualizado: read"));
    verify(chatMessageStatusService, times(2)).applyProviderStatus(eq(tenantId), any(), any(), any(), any(), any());
  }

  @Test
  void statusSemMensagemCorrespondenteNaoGeraDetalhe() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"statuses\":[{\"id\":\"m1\",\"status\":\"delivered\"}]"
        + "}}]}]}";

    when(chatMessageStatusService.applyProviderStatus(any(), any(), any(), any(), any(), any())).thenReturn(false);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.details).isEmpty();
  }

  // ---- POST receive: mensagem nao-texto -----------------------------------

  @Test
  void mensagemNaoTextoERegistradaSemChamarAssistant() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = "{\"entry\":[{\"changes\":[{\"value\":{"
        + "\"metadata\":{\"phone_number_id\":\"" + phoneNumberId + "\"},"
        + "\"messages\":[{\"id\":\"wamid-1\",\"from\":\"5511988887777\",\"type\":\"image\"}]"
        + "}}]}]}";

    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conversation();
    inbound.client = cliente();
    when(chatService.processInboundWhatsAppMessage(eq(tenantId), eq("5511988887777"), eq("[Imagem recebida]"), any(), any()))
        .thenReturn(inbound);

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.success).isEqualTo(1);
    verify(assistantApiClient, never()).processarMensagem(any(), any(), any(), any());
  }

  // ---- POST receive: opt-out -----------------------------------------------

  @Test
  void mensagemDeOptOutRegistraConsentimentoECancelaCiclosAtivos() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "PARE");

    Cliente cli = cliente();
    when(clienteRepository.findFirstByTenantIdAndPhone(tenantId, "5511988887777")).thenReturn(Optional.of(cli));
    WhatsAppBookingReactivationCycleEntity cycle = new WhatsAppBookingReactivationCycleEntity();
    when(reactivationCycleRepository.listActiveByTenantAndClient(tenantId, cli.getId()))
        .thenReturn(List.of(cycle));

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.success).isEqualTo(1);
    assertThat(cli.getWhatsappOptOut()).isTrue();
    verify(clienteRepository).save(cli);
    verify(consentHistoryRepository).save(any(ReactivationConsentHistoryEntity.class));
    verify(whatsAppBookingReactivationService).cancelCycle(cycle, "OPT_OUT");
    verify(whatsAppClient).sendMessage(eq(config), eq("5511988887777"), anyString());
    verify(chatService, never()).processInboundWhatsAppMessage(any(), any(), any(), any(), any());
  }

  @Test
  void mensagemDeOptOutSemClienteEncontradoNaoFalha() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "STOP");

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.success).isEqualTo(1);
    verify(consentHistoryRepository, never()).save(any());
  }

  @Test
  void falhaAoProcessarOptOutRetornaFalhaIsolada() {
    WhatsAppWebhookController controller = controllerWithoutSecret();
    String payload = textMessagePayload("wamid-1", "5511988887777", "SAIR");

    when(clienteRepository.findFirstByTenantIdAndPhone(any(), any()))
        .thenThrow(new RuntimeException("erro de banco"));

    WhatsAppWebhookController.WebhookResponse response = controller.receive(null, payload);

    assertThat(response.status).isEqualTo("ERROR");
    assertThat(response.failed).isEqualTo(1);
  }
}
