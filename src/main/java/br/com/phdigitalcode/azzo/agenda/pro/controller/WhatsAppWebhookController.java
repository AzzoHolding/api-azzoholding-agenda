package br.com.phdigitalcode.azzo.agenda.pro.controller;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
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
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/chat/api/webhook/WhatsAppWebhookResource.java} (939 linhas no original).
 *
 * <p>Publico (sem JWT) — {@code /webhook/**} ja esta em {@code permitAll} no {@code SecurityConfig}
 * (mesma faixa usada pelo {@link WebhookController} do Asaas e por {@link TelegramWebhookController}).
 *
 * <p>O metodo {@link #receive} e {@code @Transactional} — assim como no original — para que a
 * mutacao de {@code Cliente}/{@code ReactivationConsentHistoryEntity}/ciclo de reativacao no fluxo
 * de opt-out (que nao passa por {@code save()} explicito, igual ao Panache original) permaneça
 * gerenciada e seja persistida no commit. Isolamento de falha por mensagem/status (comentarios
 * "Fix 2" do original) e preservado com try/catch dentro do proprio metodo transacional: uma
 * excecao capturada aqui NAO propaga para fora do proxy e portanto nao marca a transacao para
 * rollback, exatamente como no Quarkus original.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

  private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookController.class);
  private static final String CHANNEL_WHATSAPP_CONFIG_ALERT = "WHATSAPP_CONFIG_ALERT";
  private static final String CHANNEL_WHATSAPP_DELIVERY_ERROR = "WHATSAPP_DELIVERY_ERROR";

  private final TenantRepository tenantRepository;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final NotificationPublisher notificationPublisher;
  private final WhatsAppClient whatsAppClient;
  private final WebhookVerifyTokenHashService webhookVerifyTokenHashService;
  private final ChatMessageStatusService chatMessageStatusService;
  private final ChatService chatService;
  private final WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private final AssistantApiClient assistantApiClient;
  private final ObjectMapper objectMapper;
  private final ReactivationConsentHistoryRepository consentHistoryRepository;
  private final WhatsAppBookingReactivationCycleRepository reactivationCycleRepository;
  private final ClienteRepository clienteRepository;
  private final LicenseStatusService licenseStatusService;

  private final String metaAppSecret;
  private final boolean debugEnabled;
  private final String activeProfile;

  public WhatsAppWebhookController(
      TenantRepository tenantRepository,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      NotificationPublisher notificationPublisher,
      WhatsAppClient whatsAppClient,
      WebhookVerifyTokenHashService webhookVerifyTokenHashService,
      ChatMessageStatusService chatMessageStatusService,
      ChatService chatService,
      WhatsAppBookingReactivationService whatsAppBookingReactivationService,
      AssistantApiClient assistantApiClient,
      ObjectMapper objectMapper,
      ReactivationConsentHistoryRepository consentHistoryRepository,
      WhatsAppBookingReactivationCycleRepository reactivationCycleRepository,
      ClienteRepository clienteRepository,
      LicenseStatusService licenseStatusService,
      @Value("${app.meta.app-secret:__unset__}") String metaAppSecret,
      @Value("${app.agenda.debug:false}") boolean debugEnabled,
      @Value("${spring.profiles.active:dev}") String activeProfile) {
    this.tenantRepository = tenantRepository;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.notificationPublisher = notificationPublisher;
    this.whatsAppClient = whatsAppClient;
    this.webhookVerifyTokenHashService = webhookVerifyTokenHashService;
    this.chatMessageStatusService = chatMessageStatusService;
    this.chatService = chatService;
    this.whatsAppBookingReactivationService = whatsAppBookingReactivationService;
    this.assistantApiClient = assistantApiClient;
    this.objectMapper = objectMapper;
    this.consentHistoryRepository = consentHistoryRepository;
    this.reactivationCycleRepository = reactivationCycleRepository;
    this.clienteRepository = clienteRepository;
    this.licenseStatusService = licenseStatusService;
    this.metaAppSecret = metaAppSecret;
    this.debugEnabled = debugEnabled;
    this.activeProfile = activeProfile;
  }

  @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> verifyWebhook(
      @RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if (!"subscribe".equals(mode) || isBlank(verifyToken) || isBlank(challenge)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification request");
    }
    if (!isValidVerifyToken(verifyToken)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid verify token");
    }
    return ResponseEntity.ok(challenge);
  }

  @Transactional
  @PostMapping
  public WebhookResponse receive(
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signatureHeader,
      @RequestBody(required = false) String rawBody) {
    WebhookResponse response = WebhookResponse.ok("Webhook processado");
    Set<String> processedInboundKeys = new HashSet<>();
    LOG.info(
        "whatsappWebhook.receive.started {}",
        CorrelatedLogging.context(
            "signaturePresent", signatureHeader != null && !signatureHeader.isBlank(),
            "payloadLength", rawBody != null ? rawBody.length() : 0));

    if (!isValidHmacSignature(signatureHeader, rawBody)) {
      LOG.warn(
          "whatsappWebhook.receive.rejected {}",
          CorrelatedLogging.context("reason", "invalid_hmac"));
      response.status = "REJECTED";
      response.message = "Assinatura invalida";
      return response;
    }

    if (debugEnabled) {
      LOG.debug(
          "whatsapp.webhook.payload.received payloadLength={} payload={}",
          rawBody != null ? rawBody.length() : 0,
          rawBody);
    }

    JsonNode payload;
    try {
      payload = rawBody == null || rawBody.isBlank() ? null : objectMapper.readTree(rawBody);
    } catch (Exception e) {
      LOG.warn(
          "whatsappWebhook.receive.invalidPayload {}",
          CorrelatedLogging.context("reason", "invalid_json", "root", CorrelatedLogging.throwableSummary(e)),
          e);
      response.status = "IGNORED";
      response.message = "Payload invalido";
      return response;
    }

    if (payload == null) {
      response.status = "IGNORED";
      response.message = "Payload vazio";
      return response;
    }

    JsonNode entries = extractEntries(payload);
    if (!entries.isArray()) {
      response.status = "IGNORED";
      response.message = "Sem entradas";
      return response;
    }

    for (JsonNode entry : entries) {
      JsonNode changes = entry.path("changes");
      if (!changes.isArray()) continue;

      for (JsonNode change : changes) {
        JsonNode value = change.path("value");
        String phoneNumberId = text(value.path("metadata").path("phone_number_id"));
        if (phoneNumberId == null) {
          response.skipped++;
          response.details.add("change ignorado: phone_number_id ausente");
          continue;
        }

        TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByPhoneNumberId(phoneNumberId).orElse(null);
        Tenant tenant = config != null ? tenantRepository.findById(config.getTenantId()).orElse(null) : null;
        if (tenant == null || config == null || !config.isWhatsappEnabled()) {
          LOG.warn(
              "whatsappWebhook.change.ignored {}",
              CorrelatedLogging.context("phoneNumberId", phoneNumberId, "reason", "tenant_not_found_or_disabled"));
          response.skipped++;
          response.details.add("change ignorado: tenant nao encontrado/habilitado para phone_number_id=" + phoneNumberId);
          continue;
        }

        // Fix 4: LicenseFilter isenta explicitamente "/webhook/*" da checagem de
        // plano (webhook nao tem JWT/SecurityIdentity para o filtro avaliar), o
        // que deixava o WhatsApp como via de acesso ao sistema mesmo com o
        // tenant suspenso/inadimplente. Aqui aplicamos a mesma regra de negocio
        // do LicenseStatusService usada nas rotas autenticadas, mas sem
        // interromper o webhook inteiro: o change e apenas ignorado.
        if (licenseStatusService.deveBloquear(tenant.getId())) {
          LOG.warn(
              "whatsappWebhook.change.ignored {}",
              CorrelatedLogging.context(
                  "phoneNumberId", phoneNumberId, "tenantId", tenant.getId(), "reason", "tenant_plan_suspended"));
          response.skipped++;
          response.details.add("change ignorado: tenant com plano suspenso/vencido tenantId=" + tenant.getId());
          continue;
        }

        JsonNode messages = value.path("messages");
        JsonNode statuses = value.path("statuses");
        processStatuses(statuses, tenant.getId(), response);
        if (!messages.isArray()) continue;

        for (JsonNode messageNode : messages) {
          String messageType = text(messageNode.path("type"));
          String from = text(messageNode.path("from"));
          String body = resolveInboundContent(messageNode, messageType);
          String providerInboundMessageId = text(messageNode.path("id"));
          LOG.info(
              "WhatsApp webhook inbound tenant={} phoneNumberId={} wamid={} type={} from={}",
              tenant.getId(),
              phoneNumberId,
              providerInboundMessageId,
              safeType(messageType),
              maskPhone(normalizePhone(from)));
          if (from == null || body == null) {
            response.skipped++;
            response.details.add("mensagem ignorada: from/body ausente");
            LOG.warn(
                "WhatsApp webhook inbound ignorado tenant={} phoneNumberId={} wamid={} motivo=from_or_body_ausente",
                tenant.getId(),
                phoneNumberId,
                providerInboundMessageId);
            continue;
          }

          String contactName = resolveContactName(value.path("contacts"), from);
          String inboundDedupKey = dedupKey(phoneNumberId, providerInboundMessageId);
          if (inboundDedupKey != null && !processedInboundKeys.add(inboundDedupKey)) {
            response.skipped++;
            response.details.add("mensagem duplicada ignorada no mesmo payload: " + providerInboundMessageId);
            LOG.warn(
                "WhatsApp webhook inbound duplicado no mesmo payload tenant={} phoneNumberId={} wamid={} from={}",
                tenant.getId(),
                phoneNumberId,
                providerInboundMessageId,
                maskPhone(normalizePhone(from)));
            continue;
          }
          // Fix 2: uma mensagem individual nao pode derrubar o batch inteiro.
          // Antes, uma RuntimeException nao capturada aqui (ex.: lancada por
          // chatService.processInboundWhatsAppMessage antes do try interno de
          // processInboundText, ou por processInboundNonText, que nao tinha
          // nenhum try/catch) escapava do loop, abortava a transacao @Transactional
          // do metodo receive() e descartava o resultado de TODAS as mensagens
          // do payload — inclusive as que ja haviam sido processadas com sucesso
          // (efeitos colaterais em REQUIRES_NEW ja commitados permaneciam, mas a
          // resposta HTTP virava 500 e o provider reenviava o payload inteiro).
          // Isolamos a falha por mensagem: registramos como failed e seguimos
          // para a proxima mensagem do mesmo payload.
          try {
            ProcessingResult result = "text".equals(messageType)
                ? processInboundText(tenant, config, normalizePhone(from), body, contactName, providerInboundMessageId)
                : processInboundNonText(tenant, normalizePhone(from), body, contactName, providerInboundMessageId, messageType);
            response.processed++;
            if (result.success) {
              response.success++;
            } else {
              response.failed++;
              response.details.add(result.detail);
            }
            if (result.outboundMessage != null && !result.outboundMessage.isBlank()) {
              OutboundPreview preview = new OutboundPreview();
              preview.to = from;
              preview.message = result.outboundMessage;
              response.outboundMessages.add(preview);
            }
          } catch (RuntimeException e) {
            response.processed++;
            response.failed++;
            String error = safeError(e.getMessage());
            response.details.add(
                "falha isolada ao processar mensagem from=" + maskPhone(normalizePhone(from))
                    + " tenant=" + tenant.getId() + ": " + error);
            LOG.error(
                "whatsappWebhook.message.isolatedFailure {}",
                CorrelatedLogging.context(
                    "tenantId", tenant.getId(),
                    "phoneNumberId", phoneNumberId,
                    "providerInboundMessageId", providerInboundMessageId,
                    "error", error,
                    "root", CorrelatedLogging.throwableSummary(e)),
                e);
          }
        }
      }
    }

    if (response.failed > 0 && response.success > 0) {
      response.status = "PARTIAL";
      response.message = "Processamento parcial: " + response.success + " sucesso(s), " + response.failed + " falha(s)";
    } else if (response.failed > 0) {
      response.status = "ERROR";
      response.message = "Falha no processamento de mensagens";
    } else {
      response.status = "OK";
      response.message = "Mensagens processadas com sucesso: " + response.success;
    }
    LOG.info(
        "whatsappWebhook.receive.completed {}",
        CorrelatedLogging.context(
            "status", response.status,
            "processed", response.processed,
            "success", response.success,
            "failed", response.failed,
            "skipped", response.skipped));
    return response;
  }

  private ProcessingResult processInboundNonText(
      Tenant tenant, String fromPhone, String body, String contactName, String providerInboundMessageId, String messageType) {
    ChatService.InboundProcessingResult inbound = chatService.processInboundWhatsAppMessage(
        tenant.getId(), fromPhone, body, contactName, providerInboundMessageId);
    if (inbound.duplicateInboundMessage) {
      LOG.warn(
          "WhatsApp webhook inbound duplicado no banco tenant={} wamid={} from={} type={}",
          tenant.getId(), providerInboundMessageId, maskPhone(fromPhone), safeType(messageType));
      return ProcessingResult.success("mensagem duplicada ignorada para " + maskPhone(fromPhone), null);
    }
    LOG.info(
        "WhatsApp webhook inbound nao-texto registrado tenant={} wamid={} from={} type={}",
        tenant.getId(), providerInboundMessageId, maskPhone(fromPhone), safeType(messageType));
    return ProcessingResult.success("mensagem " + safeType(messageType) + " registrada para " + maskPhone(fromPhone), null);
  }

  private void processStatuses(JsonNode statuses, UUID tenantId, WebhookResponse response) {
    if (statuses == null || !statuses.isArray()) return;
    for (JsonNode statusNode : statuses) {
      try {
        processSingleStatus(statusNode, tenantId, response);
      } catch (RuntimeException e) {
        // Fix 2: mesma logica de isolamento aplicada aos updates de status
        // (delivered/read/failed) — um status malformado nao pode abortar o
        // processamento dos demais status/mensagens do mesmo payload.
        String error = safeError(e.getMessage());
        response.details.add("falha isolada ao processar status: " + error);
        LOG.error(
            "whatsappWebhook.status.isolatedFailure {}",
            CorrelatedLogging.context("tenantId", tenantId, "error", error, "root", CorrelatedLogging.throwableSummary(e)),
            e);
      }
    }
  }

  private void processSingleStatus(JsonNode statusNode, UUID tenantId, WebhookResponse response) {
    String providerMessageId = text(statusNode.path("id"));
    String providerStatus = text(statusNode.path("status"));
    String errorCode = text(statusNode.path("errors").path(0).path("code"));
    String errorMessage = text(statusNode.path("errors").path(0).path("title"));
    Instant occurredAt = parseProviderTimestamp(statusNode.path("timestamp"));
    LOG.info(
        "WhatsApp webhook status tenant={} providerMessageId={} status={} errorCode={}",
        tenantId, providerMessageId, providerStatus, errorCode);

    boolean updated = chatMessageStatusService.applyProviderStatus(
        tenantId, providerMessageId, providerStatus, errorCode, errorMessage, occurredAt);
    if (updated) {
      response.details.add("status atualizado: " + providerStatus + " providerMessageId=" + providerMessageId);
    } else {
      LOG.warn(
          "WhatsApp webhook status sem mensagem correspondente tenant={} providerMessageId={} status={}",
          tenantId, providerMessageId, providerStatus);
    }
  }

  private ProcessingResult processInboundText(
      Tenant tenant, TenantWhatsAppConfig config, String fromPhone, String body, String contactName, String providerInboundMessageId) {
    // Gate opt-out: verificar ANTES de qualquer processamento
    if (isOptOutMessage(body)) {
      return processOptOut(tenant, config, fromPhone, providerInboundMessageId);
    }

    ChatService.InboundProcessingResult inbound = chatService.processInboundWhatsAppMessage(
        tenant.getId(), fromPhone, body, contactName, providerInboundMessageId);
    try {
      if (inbound.duplicateInboundMessage) {
        LOG.warn(
            "WhatsApp webhook inbound texto duplicado no banco tenant={} wamid={} from={}",
            tenant.getId(), providerInboundMessageId, maskPhone(fromPhone));
        return ProcessingResult.success("mensagem duplicada ignorada para " + maskPhone(fromPhone), null);
      }
      if (inbound.manualModeEnabled) {
        LOG.info(
            "WhatsApp webhook inbound em modo manual tenant={} wamid={} from={}",
            tenant.getId(), providerInboundMessageId, maskPhone(fromPhone));
        return ProcessingResult.success("mensagem registrada em modo manual ativo para " + maskPhone(fromPhone), null);
      }

      LOG.info("whatsapp.assistant.calling tenant={} from={} wamid={}", tenant.getId(), maskPhone(fromPhone), providerInboundMessageId);
      if (debugEnabled) {
        LOG.debug(
            "whatsapp.flow.assistant.request tenant={} from={} wamid={} message={}",
            tenant.getId(), maskPhone(fromPhone), providerInboundMessageId, body);
      }

      AssistantMessageResponse assistantResponse = assistantApiClient.processarMensagem(
          tenant.getId().toString(), fromPhone, contactName, new AssistantMessageRequest(body));

      LOG.info(
          "whatsapp.assistant.ok tenant={} from={} wamid={} stage={} replyLength={}",
          tenant.getId(),
          maskPhone(fromPhone),
          providerInboundMessageId,
          assistantResponse != null ? assistantResponse.stage : "null",
          assistantResponse != null && assistantResponse.reply != null ? assistantResponse.reply.length() : 0);
      if (debugEnabled) {
        LOG.debug(
            "whatsapp.flow.assistant.response tenant={} wamid={} reply={}",
            tenant.getId(), providerInboundMessageId, assistantResponse != null ? assistantResponse.reply : "null");
      }

      whatsAppBookingReactivationService.syncAfterAssistantTurn(
          tenant.getId(), inbound.conversation, inbound.client, body, assistantResponse);
      String outbound = formatAssistantReplyForWhatsApp(assistantResponse);
      if (outbound != null && !outbound.isBlank()) {
        if (debugEnabled) {
          LOG.debug(
              "whatsapp.flow.sending tenant={} from={} wamid={} outbound={}",
              tenant.getId(), maskPhone(fromPhone), providerInboundMessageId, outbound);
        }
        String providerMessageId = whatsAppClient.sendMessage(config, fromPhone, outbound);
        LOG.info(
            "WhatsApp webhook outbound assistant tenant={} inboundWamid={} outboundProviderMessageId={} conversationId={} clientId={}",
            tenant.getId(), providerInboundMessageId, providerMessageId, inbound.conversation.getId(), inbound.client.getId());
        if (debugEnabled) {
          LOG.debug(
              "whatsapp.flow.sent tenant={} from={} inboundWamid={} outboundProviderMessageId={}",
              tenant.getId(), maskPhone(fromPhone), providerInboundMessageId, providerMessageId);
        }
        chatService.registerOutboundAssistantMessage(
            tenant.getId(), inbound.conversation.getId(), inbound.client.getId(), outbound, providerMessageId,
            ChatMessageStatus.SENT, null, null);
      }
      return ProcessingResult.success("mensagem processada para " + maskPhone(fromPhone), outbound);
    } catch (RuntimeException e) {
      String error = safeError(e.getMessage());
      LOG.error(
          "whatsappWebhook.assistant.failed {}",
          CorrelatedLogging.context(
              "tenantId", tenant.getId(),
              "fromPhone", fromPhone,
              "providerInboundMessageId", providerInboundMessageId,
              "error", error,
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      if (isTenantConfigurationError(error)) {
        registrarNotificacaoConfiguracao(tenant, fromPhone, contactName, error);
      } else {
        registrarNotificacaoFalhaEntrega(tenant, fromPhone, contactName, error);
      }
      String fallbackMessage = "Nao foi possivel processar sua mensagem agora. Tente novamente em instantes.";
      try {
        String providerMessageId = whatsAppClient.sendMessage(config, fromPhone, fallbackMessage);
        chatService.registerOutboundAssistantMessage(
            tenant.getId(), inbound.conversation.getId(), inbound.client.getId(), fallbackMessage, providerMessageId,
            ChatMessageStatus.SENT, null, null);
      } catch (RuntimeException ignored) {
        LOG.error(
            "whatsappWebhook.fallbackReply.failed {}",
            CorrelatedLogging.context(
                "tenantId", tenant.getId(),
                "fromPhone", fromPhone,
                "providerInboundMessageId", providerInboundMessageId,
                "root", CorrelatedLogging.throwableSummary(ignored)),
            ignored);
      }
      return ProcessingResult.failure(
          "falha ao processar mensagem from=" + maskPhone(fromPhone) + " tenant=" + tenant.getId() + ": " + error,
          fallbackMessage);
    }
  }

  private void registrarNotificacaoConfiguracao(Tenant tenant, String fromPhone, String contactName, String error) {
    String requisito = detectMissingRequirement(error);
    String customerName = (contactName == null || contactName.isBlank()) ? "Cliente sem nome informado" : contactName;
    String message = "Cliente " + customerName + ", telefone " + fromPhone
        + ", esta tentando agendar pelo WhatsApp, mas falta configurar " + requisito + ".";

    notificationPublisher.publish(
        tenant.getId(), null, CHANNEL_WHATSAPP_CONFIG_ALERT, fromPhone, message, StatusNotification.FAILED, error, null, 60L * 30L);
  }

  private void registrarNotificacaoFalhaEntrega(Tenant tenant, String fromPhone, String contactName, String error) {
    String customerName = (contactName == null || contactName.isBlank()) ? "Cliente sem nome informado" : contactName;
    String message = "Cliente " + customerName + ", telefone " + fromPhone
        + ", tentou agendar pelo WhatsApp, mas houve falha no envio da resposta. Erro: " + error + ".";

    notificationPublisher.publish(
        tenant.getId(), null, CHANNEL_WHATSAPP_DELIVERY_ERROR, fromPhone, message, StatusNotification.FAILED, error, null, 60L * 10L);
  }

  private boolean isOptOutMessage(String text) {
    if (text == null) return false;
    String normalized = text.strip().toUpperCase()
        .replace("Á", "A").replace("Ã", "A").replace("á", "A").replace("ã", "A")
        .replace("É", "E").replace("Ê", "E").replace("é", "E").replace("ê", "E")
        .replace("Í", "I").replace("í", "I")
        .replace("Ó", "O").replace("Ô", "O").replace("ó", "O").replace("ô", "O")
        .replace("Ú", "U").replace("ú", "U")
        .replace("Ç", "C").replace("ç", "C");
    return List.of("PARE", "STOP", "CANCELAR", "NAO QUERO", "SAIR", "REMOVER").contains(normalized);
  }

  private ProcessingResult processOptOut(Tenant tenant, TenantWhatsAppConfig config, String fromPhone, String providerInboundMessageId) {
    try {
      // Buscar cliente pelo telefone no tenant
      Cliente client = clienteRepository.findFirstByTenantIdAndPhone(tenant.getId(), fromPhone).orElse(null);
      if (client == null) {
        // Pode ser que o número esteja normalizado de forma diferente — tentar variação
        String phoneNorm = fromPhone.replaceAll("\\D", "");
        client = clienteRepository
            .findFirstByTenantIdAndPhoneContainingNormalizedRaw(tenant.getId(), phoneNorm)
            .orElse(null);
      }

      if (client != null) {
        Instant now = Instant.now();
        client.setWhatsappOptOut(true);
        client.setWhatsappOptOutAt(now);
        client.setWhatsappOptIn(false);
        clienteRepository.save(client);

        ReactivationConsentHistoryEntity consent = new ReactivationConsentHistoryEntity();
        consent.setTenantId(tenant.getId());
        consent.setClientId(client.getId());
        consent.setAction("OPT_OUT");
        consent.setSource("WHATSAPP_REPLY");
        consentHistoryRepository.save(consent);

        // Cancelar todos os ciclos ACTIVE do cliente
        List<WhatsAppBookingReactivationCycleEntity> activeCycles =
            reactivationCycleRepository.listActiveByTenantAndClient(tenant.getId(), client.getId());
        for (WhatsAppBookingReactivationCycleEntity cycle : activeCycles) {
          whatsAppBookingReactivationService.cancelCycle(cycle, "OPT_OUT");
        }

        LOG.info("Reativacao opt-out via WhatsApp: tenantId={} clientId={} wamid={}", tenant.getId(), client.getId(), providerInboundMessageId);
      } else {
        LOG.info("Reativacao opt-out via WhatsApp: tenantId={} clientId=nao_encontrado wamid={}", tenant.getId(), providerInboundMessageId);
      }

      String confirmacao = "Entendido! Voce nao recebera mais mensagens automaticas de agendamento. "
          + "Para reativar, entre em contato com o salao.";
      whatsAppClient.sendMessage(config, fromPhone, confirmacao);
      LOG.info(
          "whatsappWebhook.optOut.completed {}",
          CorrelatedLogging.context("tenantId", tenant.getId(), "fromPhone", fromPhone, "providerInboundMessageId", providerInboundMessageId));
      return ProcessingResult.success("opt-out registrado", confirmacao);
    } catch (RuntimeException e) {
      LOG.error(
          "whatsappWebhook.optOut.failed {}",
          CorrelatedLogging.context(
              "tenantId", tenant.getId(),
              "fromPhone", fromPhone,
              "providerInboundMessageId", providerInboundMessageId,
              "error", safeError(e.getMessage()),
              "root", CorrelatedLogging.throwableSummary(e)),
          e);
      return ProcessingResult.failure("falha ao processar opt-out", null);
    }
  }

  private boolean isTenantConfigurationError(String error) {
    if (error == null) return false;
    String lower = error.toLowerCase();
    return lower.contains("token do whatsapp nao configurado")
        || lower.contains("phonenumberid do whatsapp nao configurado")
        || lower.contains("configuracao do whatsapp");
  }

  private String detectMissingRequirement(String error) {
    if (error == null) return "configuracao do WhatsApp";
    String lower = error.toLowerCase();
    if (lower.contains("token do whatsapp nao configurado")) return "token de acesso do WhatsApp";
    if (lower.contains("phonenumberid do whatsapp nao configurado")) return "phone number id do WhatsApp";
    return "configuracao do WhatsApp";
  }

  private String resolveContactName(JsonNode contacts, String from) {
    if (contacts == null || !contacts.isArray()) return null;

    for (JsonNode contact : contacts) {
      String waId = text(contact.path("wa_id"));
      String profileName = text(contact.path("profile").path("name"));
      if (profileName == null) continue;
      if (waId == null || normalizePhone(waId).equals(normalizePhone(from))) {
        return profileName;
      }
    }
    return null;
  }

  private String normalizePhone(String phone) {
    if (phone == null) return "";
    return phone.replaceAll("\\D", "");
  }

  private String maskPhone(String phone) {
    if (phone == null || phone.length() < 4) return "***";
    return phone.substring(0, 2) + "***" + phone.substring(phone.length() - 2);
  }

  private String dedupKey(String phoneNumberId, String providerInboundMessageId) {
    if (isBlank(phoneNumberId) || isBlank(providerInboundMessageId)) return null;
    return phoneNumberId.trim() + ":" + providerInboundMessageId.trim();
  }

  private String resolveInboundContent(JsonNode messageNode, String messageType) {
    if ("text".equals(messageType)) {
      return text(messageNode.path("text").path("body"));
    }
    if ("image".equals(messageType)) return "[Imagem recebida]";
    if ("audio".equals(messageType)) return "[Audio recebido]";
    if ("video".equals(messageType)) return "[Video recebido]";
    if ("document".equals(messageType)) return "[Documento recebido]";
    if ("sticker".equals(messageType)) return "[Figurinha recebida]";
    if ("location".equals(messageType)) return "[Localizacao recebida]";
    if ("contacts".equals(messageType)) return "[Contato recebido]";
    if ("button".equals(messageType)) {
      String buttonText = text(messageNode.path("button").path("text"));
      return buttonText != null ? "[Botao] " + buttonText : "[Botao recebido]";
    }
    if ("interactive".equals(messageType)) {
      String interactiveTitle = text(messageNode.path("interactive").path("button_reply").path("title"));
      if (interactiveTitle == null) {
        interactiveTitle = text(messageNode.path("interactive").path("list_reply").path("title"));
      }
      return interactiveTitle != null ? "[Interativo] " + interactiveTitle : "[Mensagem interativa recebida]";
    }
    return null;
  }

  private String safeType(String messageType) {
    return (messageType == null || messageType.isBlank()) ? "desconhecida" : messageType;
  }

  private String text(JsonNode node) {
    if (node == null || node.isNull()) return null;
    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  private String safeError(String message) {
    if (message == null || message.isBlank()) return "erro nao especificado";
    return message.length() > 220 ? message.substring(0, 220) : message;
  }

  private Instant parseProviderTimestamp(JsonNode timestampNode) {
    if (timestampNode == null || timestampNode.isMissingNode() || timestampNode.isNull()) return Instant.now();
    String raw = timestampNode.asText();
    if (raw == null || raw.isBlank()) return Instant.now();
    try {
      long epoch = Long.parseLong(raw.trim());
      return Instant.ofEpochSecond(epoch);
    } catch (Exception ignored) {
      return Instant.now();
    }
  }

  /**
   * O original declara {@code personalizeReplyWithContactName(String, String)} e passa
   * {@code contactName} para este metodo, mas NUNCA invoca aquele metodo — codigo morto
   * (confirmado por leitura linha a linha do {@code WhatsAppWebhookResource} original). Omitido
   * aqui junto com o parametro {@code contactName}, que so existia para alimentar a chamada morta;
   * o comportamento observavel (texto formatado por {@link #organizeWhatsAppText(String)}) e
   * identico ao original.
   */
  private String formatAssistantReplyForWhatsApp(AssistantMessageResponse response) {
    if (response == null || response.reply == null || response.reply.isBlank()) return null;
    return organizeWhatsAppText(response.reply.trim());
  }

  private String organizeWhatsAppText(String message) {
    if (message == null || message.isBlank()) return message;

    return message
        .replace("\r\n", "\n")
        .replaceAll(",\\s*(?=\\d+\\s-)", "\n")
        .replace(". Voce tambem pode responder 'ultimo'.", "\nVoce tambem pode responder 'ultimo'.")
        .replace(" Se quiser,", "\nSe quiser,")
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
  }

  private boolean isValidHmacSignature(String signatureHeader, String rawBody) {
    boolean secretUnset = "__unset__".equals(metaAppSecret) || metaAppSecret == null || metaAppSecret.isBlank();
    if (secretUnset) {
      // SEC-005: fail-closed em producao. Sem segredo configurado, o webhook
      // esta exposto sem forma de validar assinatura — rejeitamos por padrao.
      if (activeProfile != null && activeProfile.contains("prod")) {
        LOG.error("META_APP_SECRET nao configurado em producao. Webhook WhatsApp rejeitado (fail-closed).");
        return false;
      }
      // Em dev/test, permite trafego local mas loga aviso explicito.
      LOG.warn("META_APP_SECRET nao configurado. Validacao HMAC desabilitada (apenas ambiente nao-producao).");
      return true;
    }
    if (isBlank(signatureHeader) || !signatureHeader.startsWith("sha256=")) {
      return false;
    }
    String expectedHex = signatureHeader.substring("sha256=".length()).trim();
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(metaAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] computed = mac.doFinal((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
      String computedHex = toHex(computed);
      return MessageDigest.isEqual(
          computedHex.getBytes(StandardCharsets.UTF_8),
          expectedHex.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      LOG.error("Erro ao validar HMAC do webhook", e);
      return false;
    }
  }

  private String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  private boolean isValidVerifyToken(String verifyToken) {
    String tokenHash = webhookVerifyTokenHashService.hash(verifyToken);
    return tenantWhatsAppConfigRepository.existsByWebhookVerifyTokenHash(tokenHash);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private JsonNode extractEntries(JsonNode payload) {
    JsonNode entries = payload.path("entry");
    if (entries.isArray()) return entries;

    // Compatibilidade com payload simplificado de testes:
    // { "field": "messages", "value": { ... } }
    if (payload.has("field") && payload.has("value") && payload.path("value").isObject()) {
      return wrapSingleChange(payload.path("field"), payload.path("value"));
    }

    // Compatibilidade com payload contendo diretamente "value".
    if (payload.path("value").isObject()) {
      JsonNode field = payload.has("field") ? payload.path("field") : JsonNodeFactory.instance.textNode("messages");
      return wrapSingleChange(field, payload.path("value"));
    }

    // Compatibilidade com payload contendo diretamente o objeto "value".
    if (payload.path("metadata").isObject() || payload.path("messages").isArray()) {
      ObjectNode value = JsonNodeFactory.instance.objectNode();
      value.setAll((ObjectNode) payload);
      return wrapSingleChange(JsonNodeFactory.instance.textNode("messages"), value);
    }

    return entries;
  }

  private ArrayNode wrapSingleChange(JsonNode field, JsonNode value) {
    ObjectNode change = JsonNodeFactory.instance.objectNode();
    change.set("field", field);
    change.set("value", value);

    ArrayNode changes = JsonNodeFactory.instance.arrayNode();
    changes.add(change);

    ObjectNode entry = JsonNodeFactory.instance.objectNode();
    entry.set("changes", changes);

    ArrayNode entries = JsonNodeFactory.instance.arrayNode();
    entries.add(entry);
    return entries;
  }

  public static class WebhookResponse {
    public String status;
    public String message;
    public int processed;
    public int success;
    public int failed;
    public int skipped;
    public List<String> details = new ArrayList<>();
    public List<OutboundPreview> outboundMessages = new ArrayList<>();

    static WebhookResponse ok(String message) {
      WebhookResponse response = new WebhookResponse();
      response.status = "OK";
      response.message = message;
      return response;
    }
  }

  private static class ProcessingResult {
    final boolean success;
    final String detail;
    final String outboundMessage;

    private ProcessingResult(boolean success, String detail, String outboundMessage) {
      this.success = success;
      this.detail = detail;
      this.outboundMessage = outboundMessage;
    }

    static ProcessingResult success(String detail, String outboundMessage) {
      return new ProcessingResult(true, detail, outboundMessage);
    }

    static ProcessingResult failure(String detail, String outboundMessage) {
      return new ProcessingResult(false, detail, outboundMessage);
    }
  }

  public static class OutboundPreview {
    public String to;
    public String message;
  }
}
