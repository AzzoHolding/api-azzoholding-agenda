package br.com.phdigitalcode.azzo.agenda.pro.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Tenant;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.WebhookVerifyTokenHashService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ChatService;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Espelha o contrato de {@code modules/chat/api/webhook/TelegramWebhookResource.java}, incluindo
 * o isolamento de falha (equivalente a {@code TelegramWebhookResourceFailureIsolationUnitTest}):
 * qualquer excecao interna deve virar {@code status=ERROR} no corpo, nunca uma excecao HTTP.
 */
class TelegramWebhookControllerTest {

  private final UUID tenantId = UUID.randomUUID();

  private TenantRepository tenantRepository;
  private TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private CommunicationChannelDispatcher communicationChannelDispatcher;
  private ChatService chatService;
  private AssistantApiClient assistantApiClient;
  private TelegramWebhookController controller;

  private Tenant tenant;
  private TenantTelegramConfig config;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    tenantTelegramConfigRepository = mock(TenantTelegramConfigRepository.class);
    webhookVerifyTokenHashService = mock(WebhookVerifyTokenHashService.class);
    communicationChannelDispatcher = mock(CommunicationChannelDispatcher.class);
    chatService = mock(ChatService.class);
    assistantApiClient = mock(AssistantApiClient.class);
    controller = new TelegramWebhookController(
        tenantRepository,
        tenantTelegramConfigRepository,
        webhookVerifyTokenHashService,
        communicationChannelDispatcher,
        chatService,
        new ObjectMapper(),
        assistantApiClient);

    tenant = new Tenant();
    tenant.setId(tenantId);
    config = new TenantTelegramConfig();
    config.setTenantId(tenantId);
    config.setTelegramEnabled(true);
    config.setTelegramWebhookSecretTokenHash(null);

    when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
    when(tenantTelegramConfigRepository.findById(tenantId)).thenReturn(Optional.of(config));
  }

  @Test
  void prefixoDoRecursoEhOMesmoDoOriginal() {
    assertThat(TelegramWebhookController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/webhook/telegram");
  }

  @Test
  void tenantIdInvalidoEhIgnorado() {
    TelegramWebhookController.WebhookResponse response = controller.receive("nao-uuid", null, "{}");

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("tenantId invalido");
  }

  @Test
  void tenantSemTelegramHabilitadoEhIgnorado() {
    config.setTelegramEnabled(false);

    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, "{}");

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Tenant sem Telegram habilitado");
  }

  @Test
  void secretTokenInvalidoEhRejeitado() {
    config.setTelegramWebhookSecretTokenHash("hash-esperado");
    when(webhookVerifyTokenHashService.hash("token-errado")).thenReturn("hash-diferente");

    TelegramWebhookController.WebhookResponse response =
        controller.receive(tenantId.toString(), "token-errado", "{}");

    assertThat(response.status).isEqualTo("REJECTED");
    assertThat(response.message).isEqualTo("Secret token invalido");
  }

  @Test
  void secretTokenValidoPassa() {
    config.setTelegramWebhookSecretTokenHash("hash-esperado");
    when(webhookVerifyTokenHashService.hash("token-certo")).thenReturn("hash-esperado");

    TelegramWebhookController.WebhookResponse response =
        controller.receive(tenantId.toString(), "token-certo", "{}");

    // payload vazio -> sem mensagem processavel, mas nao rejeitado por token
    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Sem mensagem processavel");
  }

  @Test
  void payloadInvalidoEhIgnorado() {
    TelegramWebhookController.WebhookResponse response =
        controller.receive(tenantId.toString(), null, "{invalido");

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Payload invalido");
  }

  @Test
  void mensagemSemChatIdOuConteudoEhIgnorada() {
    String payload = "{\"message\":{\"chat\":{},\"from\":{}}}";

    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Mensagem sem chatId ou conteudo");
  }

  @Test
  void mensagemDuplicadaEhIgnorada() {
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conversation();
    inbound.client = cliente();
    inbound.duplicateInboundMessage = true;
    when(chatService.processInboundTelegramMessage(eq(tenantId), eq("123"), eq("ola"), any(), any()))
        .thenReturn(inbound);

    String payload = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":123},\"text\":\"ola\",\"from\":{\"first_name\":\"Joana\"}}}";
    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Mensagem duplicada");
    assertThat(response.duplicateInboundMessage).isTrue();
    verify(assistantApiClient, never()).processarMensagem(any(), any(), any(), any());
  }

  @Test
  void modoManualAtivoEhIgnorado() {
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conversation();
    inbound.client = cliente();
    inbound.manualModeEnabled = true;
    when(chatService.processInboundTelegramMessage(eq(tenantId), eq("123"), eq("ola"), any(), any()))
        .thenReturn(inbound);

    String payload = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":123},\"text\":\"ola\"}}";
    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("IGNORED");
    assertThat(response.message).isEqualTo("Modo manual ativo");
    assertThat(response.manualModeEnabled).isTrue();
    verify(assistantApiClient, never()).processarMensagem(any(), any(), any(), any());
  }

  @Test
  void fluxoFelizProcessaEEnviaRespostaDoAssistant() {
    ChatConversationEntity conv = conversation();
    Cliente cliente = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cliente;
    when(chatService.processInboundTelegramMessage(eq(tenantId), eq("123"), eq("ola"), any(), any()))
        .thenReturn(inbound);
    when(assistantApiClient.processarMensagem(eq(tenantId.toString()), eq("123"), any(), any(AssistantMessageRequest.class)))
        .thenReturn(new AssistantMessageResponse("Resposta do assistente"));
    when(communicationChannelDispatcher.sendText(any(ChannelSendCommand.class)))
        .thenReturn(ChannelSendResult.sent("wamid-1"));

    String payload = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":123},\"text\":\"ola\",\"from\":{\"first_name\":\"Joana\"}}}";
    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("OK");
    assertThat(response.conversationId).isEqualTo(conv.getId().toString());
    assertThat(response.clientId).isEqualTo(cliente.getId().toString());
    verify(communicationChannelDispatcher).sendText(
        new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, "123", "Resposta do assistente"));
    verify(chatService).registerOutboundAssistantMessage(
        tenantId, conv.getId(), cliente.getId(), "Resposta do assistente", "wamid-1", ChatMessageStatus.SENT, null, null);
  }

  @Test
  void falhaDoAssistantEnviaFallbackENaoPropagaExcecao() {
    ChatConversationEntity conv = conversation();
    Cliente cliente = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cliente;
    when(chatService.processInboundTelegramMessage(eq(tenantId), eq("123"), eq("ola"), any(), any()))
        .thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any()))
        .thenThrow(new RuntimeException("assistant indisponivel"));
    when(communicationChannelDispatcher.sendText(any(ChannelSendCommand.class)))
        .thenReturn(ChannelSendResult.sent("wamid-fallback"));

    String payload = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":123},\"text\":\"ola\"}}";
    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("OK");
    verify(chatService).registerOutboundAssistantMessage(
        eq(tenantId), eq(conv.getId()), eq(cliente.getId()),
        eq("Recebi sua mensagem, mas nao consegui concluir o processamento agora. Tente novamente em instantes."),
        eq("wamid-fallback"), eq(ChatMessageStatus.SENT), any(), any());
  }

  @Test
  void falhaAoEnviarRespostaMarcaStatusError() {
    ChatConversationEntity conv = conversation();
    Cliente cliente = cliente();
    ChatService.InboundProcessingResult inbound = new ChatService.InboundProcessingResult();
    inbound.conversation = conv;
    inbound.client = cliente;
    when(chatService.processInboundTelegramMessage(eq(tenantId), eq("123"), eq("ola"), any(), any()))
        .thenReturn(inbound);
    when(assistantApiClient.processarMensagem(any(), any(), any(), any()))
        .thenReturn(new AssistantMessageResponse("resposta"));
    when(communicationChannelDispatcher.sendText(any(ChannelSendCommand.class)))
        .thenReturn(ChannelSendResult.failed("TELEGRAM_ERROR", "canal indisponivel"));

    String payload = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":123},\"text\":\"ola\"}}";
    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("ERROR");
    assertThat(response.message).isEqualTo("canal indisponivel");
  }

  @Test
  void excecaoInesperadaNuncaEscapaSempreRetornaStatusError() {
    when(chatService.processInboundTelegramMessage(any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("erro totalmente inesperado"));

    String payload = "{\"message\":{\"message_id\":1,\"chat\":{\"id\":123},\"text\":\"ola\"}}";
    TelegramWebhookController.WebhookResponse response = controller.receive(tenantId.toString(), null, payload);

    assertThat(response.status).isEqualTo("ERROR");
    assertThat(response.message).isEqualTo("Falha interna ao processar o webhook do Telegram");
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
    return cliente;
  }
}
