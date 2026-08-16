package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
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
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/chat/api/webhook/TelegramWebhookResource.java}.
 *
 * <p>Publico (sem JWT) — o path esta em {@code permitAll} no {@code SecurityConfig}, mesma
 * abordagem ja usada em {@link WebhookController} (Asaas): o Telegram nao envia JWT, a
 * autenticacao e o secret token do path/header, validado dentro deste controller.
 *
 * <p>O Telegram considera qualquer resposta nao-2xx como falha do webhook e reenfileira a update
 * para retry. Por isso — igual ao original — {@code receive} nunca deixa uma excecao escapar:
 * qualquer falha interna e capturada e devolvida como {@code status=ERROR} no corpo, sempre com
 * HTTP 200, preservando o isolamento de falha do original (comentario do Fix do Quarkus).
 */
@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookController {

  private static final Logger LOG = LoggerFactory.getLogger(TelegramWebhookController.class);
  private static final String FALLBACK_REPLY =
      "Recebi sua mensagem, mas nao consegui concluir o processamento agora. Tente novamente em instantes.";

  private final TenantRepository tenantRepository;
  private final TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private final WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private final CommunicationChannelDispatcher communicationChannelDispatcher;
  private final ChatService chatService;
  private final ObjectMapper objectMapper;
  private final AssistantApiClient assistantApiClient;

  public TelegramWebhookController(
      TenantRepository tenantRepository,
      TenantTelegramConfigRepository tenantTelegramConfigRepository,
      WebhookVerifyTokenHashService webhookVerifyTokenHashService,
      CommunicationChannelDispatcher communicationChannelDispatcher,
      ChatService chatService,
      ObjectMapper objectMapper,
      AssistantApiClient assistantApiClient) {
    this.tenantRepository = tenantRepository;
    this.tenantTelegramConfigRepository = tenantTelegramConfigRepository;
    this.webhookVerifyTokenHashService = webhookVerifyTokenHashService;
    this.communicationChannelDispatcher = communicationChannelDispatcher;
    this.chatService = chatService;
    this.objectMapper = objectMapper;
    this.assistantApiClient = assistantApiClient;
  }

  @PostMapping("/{tenantId}")
  public WebhookResponse receive(
      @PathVariable("tenantId") String tenantIdRaw,
      @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretTokenHeader,
      @RequestBody(required = false) String rawBody) {
    try {
      return handle(tenantIdRaw, secretTokenHeader, rawBody);
    } catch (RuntimeException ex) {
      LOG.error(
          "telegramWebhook.receive.unhandled {}",
          CorrelatedLogging.context("tenantId", tenantIdRaw, "root", CorrelatedLogging.throwableSummary(ex)),
          ex);
      WebhookResponse response = new WebhookResponse();
      response.status = "ERROR";
      response.message = "Falha interna ao processar o webhook do Telegram";
      return response;
    }
  }

  private WebhookResponse handle(String tenantIdRaw, String secretTokenHeader, String rawBody) {
    WebhookResponse response = new WebhookResponse();
    response.status = "OK";
    response.message = "Webhook processado";

    UUID tenantId = parseTenantId(tenantIdRaw);
    if (tenantId == null) {
      response.status = "IGNORED";
      response.message = "tenantId invalido";
      return response;
    }

    Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
    TenantTelegramConfig config = tenantTelegramConfigRepository.findById(tenantId).orElse(null);
    if (tenant == null || config == null || !config.isTelegramEnabled()) {
      response.status = "IGNORED";
      response.message = "Tenant sem Telegram habilitado";
      return response;
    }
    if (!isSecretTokenValid(config, secretTokenHeader)) {
      LOG.warn(
          "telegramWebhook.receive.rejected {}",
          CorrelatedLogging.context("tenantId", tenantId, "reason", "invalid_secret_token"));
      response.status = "REJECTED";
      response.message = "Secret token invalido";
      return response;
    }

    JsonNode payload;
    try {
      payload = rawBody == null || rawBody.isBlank() ? null : objectMapper.readTree(rawBody);
    } catch (Exception ex) {
      LOG.warn(
          "telegramWebhook.receive.invalidPayload {}",
          CorrelatedLogging.context("tenantId", tenantId, "root", CorrelatedLogging.throwableSummary(ex)),
          ex);
      response.status = "IGNORED";
      response.message = "Payload invalido";
      return response;
    }

    JsonNode messageNode = payload == null ? null : firstMessageNode(payload);
    if (messageNode == null) {
      response.status = "IGNORED";
      response.message = "Sem mensagem processavel";
      return response;
    }

    String chatId = text(messageNode.path("chat").path("id"));
    String content = firstNonBlank(text(messageNode.path("text")), text(messageNode.path("caption")));
    String contactName = buildContactName(messageNode.path("from"));
    String providerMessageId = text(messageNode.path("message_id"));
    if (chatId == null || content == null) {
      response.status = "IGNORED";
      response.message = "Mensagem sem chatId ou conteudo";
      return response;
    }

    LOG.info(
        "telegramWebhook.receive.inbound {}",
        CorrelatedLogging.context("tenantId", tenantId, "chatId", chatId, "providerMessageId", providerMessageId));

    ChatService.InboundProcessingResult inbound =
        chatService.processInboundTelegramMessage(tenantId, chatId, content, contactName, providerMessageId);

    response.conversationId =
        inbound.conversation != null && inbound.conversation.getId() != null
            ? inbound.conversation.getId().toString()
            : null;
    response.clientId =
        inbound.client != null && inbound.client.getId() != null ? inbound.client.getId().toString() : null;
    response.manualModeEnabled = inbound.manualModeEnabled;
    response.duplicateInboundMessage = inbound.duplicateInboundMessage;

    if (inbound.duplicateInboundMessage) {
      response.status = "IGNORED";
      response.message = "Mensagem duplicada";
      return response;
    }
    if (inbound.manualModeEnabled) {
      response.status = "IGNORED";
      response.message = "Modo manual ativo";
      return response;
    }

    String outboundReply = null;
    ChatMessageStatus outboundStatus = ChatMessageStatus.SENT;
    String outboundProviderMessageId = null;
    String outboundErrorCode = null;
    String outboundErrorMessage = null;
    try {
      AssistantMessageResponse assistantResponse = assistantApiClient.processarMensagem(
          tenant.getId().toString(), chatId, contactName, new AssistantMessageRequest(content));
      outboundReply = normalizeAssistantReply(assistantResponse);
      if (outboundReply == null) {
        response.status = "IGNORED";
        response.message = "Assistant sem resposta";
        return response;
      }

      ChannelSendResult sendResult = communicationChannelDispatcher.sendText(
          new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, chatId, outboundReply));
      if (!sendResult.success()) {
        outboundStatus = ChatMessageStatus.FAILED;
        outboundErrorCode = sendResult.providerErrorCode();
        outboundErrorMessage = sendResult.providerErrorMessage();
      } else {
        outboundProviderMessageId = sendResult.providerMessageId();
      }
    } catch (RuntimeException ex) {
      LOG.error(
          "telegramWebhook.receive.assistantFailed {}",
          CorrelatedLogging.context(
              "tenantId", tenantId,
              "chatId", chatId,
              "conversationId", response.conversationId,
              "root", CorrelatedLogging.throwableSummary(ex)),
          ex);
      outboundReply = FALLBACK_REPLY;
      ChannelSendResult sendResult = communicationChannelDispatcher.sendText(
          new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, chatId, outboundReply));
      if (!sendResult.success()) {
        outboundStatus = ChatMessageStatus.FAILED;
        outboundErrorCode = firstNonBlank(sendResult.providerErrorCode(), "TELEGRAM_ASSISTANT_FALLBACK_ERROR");
        outboundErrorMessage = firstNonBlank(sendResult.providerErrorMessage(), ex.getMessage());
      } else {
        outboundProviderMessageId = sendResult.providerMessageId();
      }
    }

    if (outboundReply != null) {
      chatService.registerOutboundAssistantMessage(
          tenantId,
          inbound.conversation.getId(),
          inbound.client.getId(),
          outboundReply,
          outboundProviderMessageId,
          outboundStatus,
          outboundErrorCode,
          outboundErrorMessage);
    }
    if (outboundStatus == ChatMessageStatus.FAILED) {
      response.status = "ERROR";
      response.message = firstNonBlank(outboundErrorMessage, "Falha ao enviar resposta Telegram");
    }
    return response;
  }

  private UUID parseTenantId(String rawValue) {
    try {
      return UUID.fromString(rawValue);
    } catch (Exception ex) {
      return null;
    }
  }

  private boolean isSecretTokenValid(TenantTelegramConfig config, String rawSecretToken) {
    if (config == null) return false;
    String expectedHash = normalize(config.getTelegramWebhookSecretTokenHash());
    if (expectedHash == null) return true;
    String candidateHash = webhookVerifyTokenHashService.hash(rawSecretToken);
    return expectedHash.equals(candidateHash);
  }

  private JsonNode firstMessageNode(JsonNode payload) {
    JsonNode messageNode = payload.path("message");
    if (!messageNode.isMissingNode() && !messageNode.isNull()) return messageNode;
    JsonNode editedMessage = payload.path("edited_message");
    if (!editedMessage.isMissingNode() && !editedMessage.isNull()) return editedMessage;
    return null;
  }

  private String normalizeAssistantReply(AssistantMessageResponse assistantResponse) {
    if (assistantResponse == null) return null;
    return normalize(assistantResponse.reply);
  }

  private String buildContactName(JsonNode fromNode) {
    return firstNonBlank(
        text(fromNode.path("first_name")), text(fromNode.path("username")), text(fromNode.path("last_name")));
  }

  private String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    String value = node.asText(null);
    return normalize(value);
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = normalize(value);
      if (normalized != null) return normalized;
    }
    return null;
  }

  private String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  public static class WebhookResponse {
    public String status;
    public String message;
    public String conversationId;
    public String clientId;
    public boolean manualModeEnabled;
    public boolean duplicateInboundMessage;
  }
}
