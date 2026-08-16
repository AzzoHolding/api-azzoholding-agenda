package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;

import br.com.phdigitalcode.azzo.agenda.pro.dto.chat.ChatDtos;
import br.com.phdigitalcode.azzo.agenda.pro.exception.ApiClientErrorException;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatMessageEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatAppointmentMarker;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageDirection;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditConstants;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditEventCommand;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AuditService;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.security.EncryptionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/chat/application/ChatService.java}.
 *
 * <p>O CDI self-injection do original ({@code @Inject ChatService self}, usado para invocar
 * {@code persistNew*InNewTransaction} atraves do proxy e assim aplicar {@code REQUIRES_NEW}) vira,
 * em Spring, um {@link TransactionTemplate} configurado com
 * {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW} — mesmo padrao ja usado em
 * {@code AppointmentNoShowProcessingService}/{@code ChatMessageStatusService}. Isso evita o
 * problema de auto-invocacao de proxies Spring sem precisar de um bean "self" artificial.
 *
 * <p>{@code jakarta.persistence.PersistenceException} (Quarkus/Panache) vira
 * {@link DataIntegrityViolationException} (traducao de excecao padrao do Spring Data para
 * violacao de constraint unica) nos dois pontos de corrida documentados no original
 * (migration V112): criacao de {@code Cliente} por telefone e de {@code ChatConversationEntity}
 * por tenant+cliente+canal.
 *
 * <p>{@code ServicoClientes} era injetado no original mas nunca utilizado em nenhum metodo desta
 * classe — omitido aqui para nao introduzir uma dependencia morta (regra "sem codigo morto"),
 * sem alterar nenhum comportamento observavel.
 */
@Service
public class ChatService {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");
  private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

  private final ContextoTenant contextoTenant;
  private final ChatConversationRepository chatConversationRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ClienteRepository clienteRepository;
  private final CommunicationChannelDispatcher communicationChannelDispatcher;
  private final CustomerCommunicationChannelResolver customerCommunicationChannelResolver;
  private final AuditService auditService;
  private final AuthenticatedUser authenticatedUser;
  private final EncryptionService encryptionService;
  private final ChatRealtimePublisher chatRealtimePublisher;
  private final WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private final TransactionTemplate requiresNewTransaction;

  private final int retentionCompletedDays;
  private final int retentionCanceledDays;
  private final int retentionDefaultDays;
  private final int manualModeDurationHours;

  public ChatService(
      ContextoTenant contextoTenant,
      ChatConversationRepository chatConversationRepository,
      ChatMessageRepository chatMessageRepository,
      ClienteRepository clienteRepository,
      CommunicationChannelDispatcher communicationChannelDispatcher,
      CustomerCommunicationChannelResolver customerCommunicationChannelResolver,
      AuditService auditService,
      AuthenticatedUser authenticatedUser,
      EncryptionService encryptionService,
      ChatRealtimePublisher chatRealtimePublisher,
      WhatsAppBookingReactivationService whatsAppBookingReactivationService,
      PlatformTransactionManager transactionManager,
      @Value("${app.chat.retention.days.completed:1}") int retentionCompletedDays,
      @Value("${app.chat.retention.days.canceled:2}") int retentionCanceledDays,
      @Value("${app.chat.retention.days.default:5}") int retentionDefaultDays,
      @Value("${app.chat.manual-mode.duration-hours:24}") int manualModeDurationHours) {
    this.contextoTenant = contextoTenant;
    this.chatConversationRepository = chatConversationRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.clienteRepository = clienteRepository;
    this.communicationChannelDispatcher = communicationChannelDispatcher;
    this.customerCommunicationChannelResolver = customerCommunicationChannelResolver;
    this.auditService = auditService;
    this.authenticatedUser = authenticatedUser;
    this.encryptionService = encryptionService;
    this.chatRealtimePublisher = chatRealtimePublisher;
    this.whatsAppBookingReactivationService = whatsAppBookingReactivationService;
    this.requiresNewTransaction = new TransactionTemplate(transactionManager);
    this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.retentionCompletedDays = retentionCompletedDays;
    this.retentionCanceledDays = retentionCanceledDays;
    this.retentionDefaultDays = retentionDefaultDays;
    this.manualModeDurationHours = manualModeDurationHours;
  }

  public ChatDtos.ConversationListResponse listConversations(Integer page, Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int pg = normalizePage(page);
    int ps = normalizePageSize(pageSize);
    int offset = (pg - 1) * ps;

    List<ChatConversationRepository.ConversationWithClientRow> rows =
        chatConversationRepository.listPagedWithClient(tenantId, offset, ps);
    ChatDtos.ConversationListResponse response = new ChatDtos.ConversationListResponse();
    response.items = rows.stream().map(this::toConversationDtoFromRow).toList();
    response.total = chatConversationRepository.countByTenant(tenantId);
    response.page = pg;
    response.pageSize = ps;
    return response;
  }

  public ChatDtos.ConversationListResponse listTodayConversations(Integer page, Integer pageSize) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    int pg = normalizePage(page);
    int ps = normalizePageSize(pageSize);
    int offset = (pg - 1) * ps;

    Instant dayStart = LocalDate.now(ZONA_BR).atStartOfDay(ZONA_BR).toInstant();
    Instant nextDayStart = LocalDate.now(ZONA_BR).plusDays(1).atStartOfDay(ZONA_BR).toInstant();

    List<ChatConversationRepository.ConversationWithClientRow> rows =
        chatConversationRepository.listTodayPagedWithClient(tenantId, dayStart, nextDayStart, offset, ps);
    ChatDtos.ConversationListResponse response = new ChatDtos.ConversationListResponse();
    response.items = rows.stream().map(this::toConversationDtoFromRow).toList();
    response.total = chatConversationRepository.countTodayByTenant(tenantId, dayStart, nextDayStart);
    response.page = pg;
    response.pageSize = ps;
    return response;
  }

  public ChatDtos.MessageListResponse listMessages(String conversationId, Integer page, Integer pageSize) {
    return listMessages(conversationId, page, pageSize, null);
  }

  public ChatDtos.MessageListResponse listMessages(
      String conversationId, Integer page, Integer pageSize, String cursor) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID convId = parseUuid(conversationId, "conversationId invalido.");
    chatConversationRepository.findByTenantAndId(tenantId, convId)
        .orElseThrow(() -> notFound("Conversa nao encontrada."));

    int ps = normalizePageSize(pageSize);
    UUID cursorId = null;
    if (cursor != null && !cursor.isBlank()) {
      try {
        cursorId = UUID.fromString(cursor.trim());
      } catch (Exception ignored) {
        // cursor invalido: ignora e pagina desde o inicio, mesmo comportamento do original.
      }
    }

    List<ChatMessageEntity> rows =
        chatMessageRepository.listByConversationAfterCursor(tenantId, convId, cursorId, ps + 1);
    boolean hasNext = rows.size() > ps;
    if (hasNext) rows = rows.subList(0, ps);

    ChatDtos.MessageListResponse response = new ChatDtos.MessageListResponse();
    response.items = rows.stream().map(this::toMessageDto).toList();
    response.total = chatMessageRepository.countByConversation(tenantId, convId);
    response.page = normalizePage(page);
    response.pageSize = ps;
    response.hasNext = hasNext;
    response.nextCursor = hasNext && !rows.isEmpty() ? rows.get(rows.size() - 1).getId().toString() : null;
    return response;
  }

  @Transactional
  public ChatDtos.SendMessageResponse sendMessage(ChatDtos.SendMessageRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (request == null) throw new IllegalArgumentException("Payload de envio obrigatorio.");
    UUID clientId = parseUuid(request.clientId, "clientId invalido.");
    String content = normalize(request.content);
    if (content == null || content.isBlank()) throw new IllegalArgumentException("Conteudo da mensagem obrigatorio.");

    Cliente client = clienteRepository.findByIdAndTenantId(clientId, tenantId).orElse(null);
    if (client == null) throw notFound("Cliente nao encontrado.");
    // Gate LGPD: nao enviar mensagem a cliente com opt-out registrado
    if (Boolean.TRUE.equals(client.getWhatsappOptOut())) {
      throw new ApiClientErrorException(
          "Cliente optou por nao receber mensagens pelo WhatsApp.", HttpStatus.UNPROCESSABLE_ENTITY.value());
    }
    OutboundRoute outboundRoute = resolveOutboundRoute(tenantId, client);
    String destination = outboundRoute.destination();
    LOG.info(
        "chat.sendMessage.started {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "clientId", clientId,
            "channel", outboundRoute.channel(),
            "destination", destination));

    ChatConversationEntity conversation = outboundRoute.conversation() != null
        ? outboundRoute.conversation()
        : createConversation(tenantId, clientId, destination, outboundRoute.channel());

    Instant now = Instant.now();
    ChatMessageEntity message = new ChatMessageEntity();
    message.setTenantId(tenantId);
    message.setConversationId(conversation.getId());
    message.setClientId(clientId);
    message.setDirection(ChatMessageDirection.OUTBOUND);
    message.setContent(encryptContent(content));
    message.setStatus(ChatMessageStatus.QUEUED);
    message.setExpiresAt(now.plusSeconds(retentionDaysFor(conversation.getAppointmentMarker()) * 86400L));
    chatMessageRepository.save(message);
    if (actorCanEnableManualMode()) {
      enableManualMode(conversation, obterUsuarioId(), "HUMAN_OUTBOUND_MESSAGE");
    }

    ChannelSendResult sendResult = communicationChannelDispatcher.sendText(
        new ChannelSendCommand(tenantId, outboundRoute.channel(), destination, content));
    if (!sendResult.success()) {
      message.setStatus(ChatMessageStatus.FAILED);
      message.setFailedAt(Instant.now());
      message.setProviderErrorCode(sendResult.providerErrorCode());
      message.setProviderErrorMessage(safeError(sendResult.providerErrorMessage()));
      updateConversationPreview(conversation, content, message.getFailedAt());
      chatRealtimePublisher.publishChatUpdate(tenantId, conversation.getId(), client.getId(), "OUTBOUND_FAILED");
      auditarChat(
          tenantId,
          "CHAT_MESSAGE_SEND",
          AuditConstants.Status.ERROR,
          null,
          resumoMensagem(message, destination),
          message.getId(),
          message.getProviderErrorCode(),
          message.getProviderErrorMessage());
      LOG.warn(
          "chat.sendMessage.failedByChannel {}",
          CorrelatedLogging.context(
              "tenantId", tenantId,
              "clientId", clientId,
              "conversationId", conversation.getId(),
              "channel", outboundRoute.channel(),
              "providerErrorCode", message.getProviderErrorCode()));
      return buildSendMessageResponse(message, conversation);
    }

    message.setStatus(ChatMessageStatus.SENT);
    message.setSentAt(Instant.now());
    message.setProviderMessageId(sendResult.providerMessageId());
    updateConversationPreview(conversation, content, message.getSentAt());
    chatRealtimePublisher.publishChatUpdate(tenantId, conversation.getId(), client.getId(), "OUTBOUND_SENT");
    auditarChat(
        tenantId,
        "CHAT_MESSAGE_SEND",
        AuditConstants.Status.SUCCESS,
        null,
        resumoMensagem(message, destination),
        message.getId(),
        null,
        null);
    LOG.info(
        "chat.sendMessage.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "clientId", clientId,
            "conversationId", conversation.getId(),
            "messageId", message.getId(),
            "providerMessageId", message.getProviderMessageId(),
            "channel", outboundRoute.channel()));

    return buildSendMessageResponse(message, conversation);
  }

  @Transactional
  public ChatDtos.UpdateMarkerResponse updateMarker(String conversationId, ChatDtos.UpdateMarkerRequest request) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    if (request == null) throw new IllegalArgumentException("Payload de marcador obrigatorio.");
    UUID convId = parseUuid(conversationId, "conversationId invalido.");
    ChatConversationEntity conversation = chatConversationRepository.findByTenantAndId(tenantId, convId)
        .orElseThrow(() -> notFound("Conversa nao encontrada."));

    Map<String, Object> before = resumoMarcador(conversation);
    ChatAppointmentMarker marker = ChatAppointmentMarker.from(request.appointmentMarker);
    conversation.setAppointmentMarker(marker);
    conversation.setUpdatedAt(Instant.now());
    chatMessageRepository.updateExpiresAtByConversation(
        tenantId, convId, Instant.now().plusSeconds(retentionDaysFor(marker) * 86400L));
    chatRealtimePublisher.publishChatUpdate(tenantId, conversation.getId(), conversation.getClientId(), "MARKER_UPDATED");
    auditarChat(
        tenantId,
        "CHAT_MARKER_UPDATE",
        AuditConstants.Status.SUCCESS,
        before,
        resumoMarcador(conversation),
        conversation.getId(),
        null,
        null);

    ChatDtos.UpdateMarkerResponse response = new ChatDtos.UpdateMarkerResponse();
    response.conversationId = conversation.getId().toString();
    response.appointmentMarker = marker.name();
    response.updatedAt = conversation.getUpdatedAt().toString();
    return response;
  }

  @Transactional
  public void deleteConversation(String conversationId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID convId = parseUuid(conversationId, "conversationId invalido.");
    ChatConversationEntity conversation = chatConversationRepository.findByTenantAndId(tenantId, convId)
        .orElseThrow(() -> notFound("Conversa nao encontrada."));
    Map<String, Object> snapshot = resumoMarcador(conversation);
    chatMessageRepository.deleteByTenantIdAndConversationId(tenantId, convId);
    chatConversationRepository.delete(conversation);
    auditarChat(tenantId, "CHAT_CONVERSATION_DELETE", AuditConstants.Status.SUCCESS,
        snapshot, null, convId, null, null);
    LOG.info(
        "chat.deleteConversation.completed {}",
        CorrelatedLogging.context("tenantId", tenantId, "conversationId", convId));
  }

  @Transactional
  public void markConversationRead(String conversationId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID convId = parseUuid(conversationId, "conversationId invalido.");
    ChatConversationEntity conversation = chatConversationRepository.findByTenantAndId(tenantId, convId)
        .orElseThrow(() -> notFound("Conversa nao encontrada."));
    Instant now = Instant.now();
    chatMessageRepository.markInboundMessagesRead(tenantId, convId, now);
    chatConversationRepository.clearUnread(tenantId, convId);
    chatRealtimePublisher.publishChatUpdate(tenantId, convId, conversation.getClientId(), "CONVERSATION_READ");
    auditarChat(tenantId, "CHAT_READ_MARK", AuditConstants.Status.SUCCESS, null,
        Map.of("conversationId", convId.toString()), convId, null, null);
    LOG.info(
        "chat.markConversationRead.completed {}",
        CorrelatedLogging.context("tenantId", tenantId, "conversationId", convId, "clientId", conversation.getClientId()));
  }

  /**
   * O inbound do WhatsApp precisa ser confirmado em transacao propria antes de chamar o
   * assistant. Assim, retries paralelos do mesmo wamid encontram o registro ja persistido e nao
   * disparam respostas duplicadas.
   */
  public InboundProcessingResult processInboundWhatsAppMessage(
      UUID tenantId, String fromPhone, String content, String contactName, String providerMessageId) {
    return requiresNewTransaction.execute(status -> processInboundMessage(
        tenantId,
        ChatChannel.WHATSAPP,
        normalizeDigits(fromPhone),
        content,
        contactName,
        providerMessageId));
  }

  public InboundProcessingResult processInboundTelegramMessage(
      UUID tenantId, String chatId, String content, String contactName, String providerMessageId) {
    return requiresNewTransaction.execute(status -> processInboundMessage(
        tenantId,
        ChatChannel.TELEGRAM,
        normalize(chatId),
        content,
        contactName,
        providerMessageId));
  }

  private InboundProcessingResult processInboundMessage(
      UUID tenantId,
      ChatChannel channel,
      String externalContactId,
      String content,
      String contactName,
      String providerMessageId) {
    String normalizedExternalContactId = normalize(channel == ChatChannel.WHATSAPP
        ? normalizeDigits(externalContactId)
        : externalContactId);
    String normalizedContent = normalize(content);
    if (tenantId == null
        || channel == null
        || normalizedExternalContactId == null
        || normalizedExternalContactId.isBlank()
        || normalizedContent == null
        || normalizedContent.isBlank()) {
      throw new IllegalArgumentException("Mensagem inbound invalida para processamento.");
    }

    String normalizedProviderMessageId = normalize(providerMessageId);
    LOG.info(
        "chat.inbound.started {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "channel", channel,
            "externalContactId", normalizedExternalContactId,
            "providerMessageId", normalizedProviderMessageId));
    if (normalizedProviderMessageId != null) {
      ChatMessageEntity existingMessage = chatMessageRepository
          .findByTenantIdAndProviderMessageId(tenantId, normalizedProviderMessageId)
          .orElse(null);
      if (existingMessage != null) {
        LOG.warn(
            "chat.inbound.duplicate {}",
            CorrelatedLogging.context(
                "tenantId", tenantId,
                "channel", channel,
                "providerMessageId", normalizedProviderMessageId,
                "conversationId", existingMessage.getConversationId(),
                "clientId", existingMessage.getClientId()));
        return buildDuplicateInboundResult(tenantId, existingMessage, normalizedExternalContactId);
      }
    }

    Cliente client = resolveOrCreateClient(tenantId, channel, normalizedExternalContactId, contactName);
    ChatConversationEntity conversation = resolveOrCreateConversation(
        tenantId, client, channel, normalizedExternalContactId);
    if (conversation.getExternalContactId() == null || conversation.getExternalContactId().isBlank()) {
      conversation.setExternalContactId(normalizedExternalContactId);
    }

    Instant now = Instant.now();
    ChatMessageEntity message = new ChatMessageEntity();
    message.setTenantId(tenantId);
    message.setConversationId(conversation.getId());
    message.setClientId(client.getId());
    message.setDirection(ChatMessageDirection.INBOUND);
    message.setContent(encryptContent(normalizedContent));
    message.setStatus(ChatMessageStatus.DELIVERED);
    message.setProviderMessageId(normalizedProviderMessageId);
    message.setDeliveredAt(now);
    message.setExpiresAt(now.plusSeconds(retentionDaysFor(conversation.getAppointmentMarker()) * 86400L));
    try {
      chatMessageRepository.saveAndFlush(message);
    } catch (DataIntegrityViolationException exception) {
      if (normalizedProviderMessageId != null) {
        ChatMessageEntity existingMessage = chatMessageRepository
            .findByTenantIdAndProviderMessageId(tenantId, normalizedProviderMessageId)
            .orElse(null);
        if (existingMessage != null) {
          LOG.warn(
              "chat.inbound.duplicate.persist {}",
              CorrelatedLogging.context(
                  "tenantId", tenantId,
                  "channel", channel,
                  "providerMessageId", normalizedProviderMessageId,
                  "conversationId", existingMessage.getConversationId(),
                  "clientId", existingMessage.getClientId()));
          return buildDuplicateInboundResult(tenantId, existingMessage, normalizedExternalContactId);
        }
      }
      throw exception;
    }

    updateConversationPreview(conversation, normalizedContent, now);
    chatConversationRepository.incrementUnread(tenantId, conversation.getId());
    chatRealtimePublisher.publishChatUpdate(tenantId, conversation.getId(), client.getId(), "INBOUND_RECEIVED");

    boolean manualModeEnabled = isManualModeActive(conversation, now);
    if (manualModeEnabled) {
      if (channel == ChatChannel.WHATSAPP) {
        whatsAppBookingReactivationService.cancelCyclesForManualMode(conversation);
      }
      auditarChat(
          tenantId,
          "CHAT_MANUAL_MODE_INBOUND_IGNORED",
          AuditConstants.Status.SUCCESS,
          null,
          Map.of(
              "conversationId", conversation.getId() != null ? conversation.getId().toString() : null,
              "clientId", client.getId() != null ? client.getId().toString() : null,
              "channel", channel.name(),
              "manualModeUntil", conversation.getManualModeUntil() != null ? conversation.getManualModeUntil().toString() : null),
          conversation.getId(),
          null,
          null);
    } else if (conversation.getManualModeUntil() != null && !conversation.getManualModeUntil().isAfter(now)) {
      clearManualMode(conversation);
      auditarChat(
          tenantId,
          "CHAT_MANUAL_MODE_EXPIRED",
          AuditConstants.Status.SUCCESS,
          null,
          resumoMarcador(conversation),
          conversation.getId(),
          null,
          null);
    }

    InboundProcessingResult result = new InboundProcessingResult();
    result.client = client;
    result.conversation = conversation;
    result.manualModeEnabled = manualModeEnabled;
    result.normalizedPhone = channel == ChatChannel.WHATSAPP ? normalizedExternalContactId : null;
    result.externalContactId = normalizedExternalContactId;
    if (channel == ChatChannel.WHATSAPP) {
      whatsAppBookingReactivationService.markClientReplyIfNeeded(tenantId, client.getId(), normalizedExternalContactId);
    }
    LOG.info(
        "chat.inbound.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "channel", channel,
            "conversationId", conversation.getId(),
            "clientId", client.getId(),
            "providerMessageId", normalizedProviderMessageId,
            "manualModeEnabled", manualModeEnabled));
    return result;
  }

  private InboundProcessingResult buildDuplicateInboundResult(
      UUID tenantId, ChatMessageEntity existingMessage, String externalContactId) {
    ChatConversationEntity conversation = chatConversationRepository
        .findByTenantAndId(tenantId, existingMessage.getConversationId())
        .orElseThrow(() -> notFound("Conversa nao encontrada para mensagem duplicada."));
    Cliente client = clienteRepository.findById(existingMessage.getClientId()).orElse(null);
    if (client == null) {
      throw notFound("Cliente nao encontrado para mensagem duplicada.");
    }

    InboundProcessingResult result = new InboundProcessingResult();
    result.client = client;
    result.conversation = conversation;
    result.manualModeEnabled = isManualModeActive(conversation, Instant.now());
    result.normalizedPhone = conversation.getChannel() == ChatChannel.WHATSAPP ? externalContactId : null;
    result.externalContactId = externalContactId;
    result.duplicateInboundMessage = true;
    return result;
  }

  public ChatMessageEntity registerOutboundAssistantMessage(
      UUID tenantId,
      UUID conversationId,
      UUID clientId,
      String content,
      String providerMessageId,
      ChatMessageStatus status,
      String providerErrorCode,
      String providerErrorMessage) {
    return requiresNewTransaction.execute(txStatus -> registerOutboundAssistantMessageInternal(
        tenantId, conversationId, clientId, content, providerMessageId, status, providerErrorCode, providerErrorMessage));
  }

  private ChatMessageEntity registerOutboundAssistantMessageInternal(
      UUID tenantId,
      UUID conversationId,
      UUID clientId,
      String content,
      String providerMessageId,
      ChatMessageStatus status,
      String providerErrorCode,
      String providerErrorMessage) {
    String normalizedContent = normalize(content);
    if (tenantId == null || conversationId == null || clientId == null || normalizedContent == null) {
      throw new IllegalArgumentException("Mensagem outbound invalida para persistencia.");
    }
    ChatConversationEntity conversation = chatConversationRepository.findByTenantAndId(tenantId, conversationId)
        .orElseThrow(() -> notFound("Conversa nao encontrada para persistencia outbound."));

    Instant now = Instant.now();
    ChatMessageEntity message = new ChatMessageEntity();
    message.setTenantId(tenantId);
    message.setConversationId(conversationId);
    message.setClientId(clientId);
    message.setDirection(ChatMessageDirection.OUTBOUND);
    message.setContent(encryptContent(normalizedContent));
    message.setStatus(status == null ? ChatMessageStatus.SENT : status);
    message.setProviderMessageId(normalize(providerMessageId));
    message.setProviderErrorCode(normalize(providerErrorCode));
    message.setProviderErrorMessage(safeError(providerErrorMessage));
    message.setExpiresAt(now.plusSeconds(retentionDaysFor(conversation.getAppointmentMarker()) * 86400L));

    if (message.getStatus() == ChatMessageStatus.FAILED) {
      message.setFailedAt(now);
    } else {
      message.setSentAt(now);
    }

    chatMessageRepository.save(message);
    updateConversationPreview(conversation, normalizedContent, now);
    chatRealtimePublisher.publishChatUpdate(tenantId, conversation.getId(), clientId, "OUTBOUND_ASSISTANT");
    if (message.getStatus() == ChatMessageStatus.FAILED) {
      LOG.warn(
          "chat.outboundAssistant.failed {}",
          CorrelatedLogging.context(
              "tenantId", tenantId,
              "conversationId", conversationId,
              "clientId", clientId,
              "providerMessageId", message.getProviderMessageId(),
              "providerErrorCode", message.getProviderErrorCode(),
              "providerErrorMessage", message.getProviderErrorMessage()));
    } else {
      LOG.info(
          "chat.outboundAssistant.completed {}",
          CorrelatedLogging.context(
              "tenantId", tenantId,
              "conversationId", conversationId,
              "clientId", clientId,
              "providerMessageId", message.getProviderMessageId()));
    }
    return message;
  }

  private ChatConversationEntity resolveOrCreateConversation(
      UUID tenantId, Cliente client, ChatChannel channel, String externalContactId) {
    if (channel == ChatChannel.WHATSAPP) {
      return chatConversationRepository
          .findByTenantClientAndChannel(tenantId, client.getId(), channel)
          .orElseGet(() -> createConversation(tenantId, client.getId(), externalContactId, channel));
    }
    return chatConversationRepository
        .findByTenantAndExternalContactId(tenantId, externalContactId, channel)
        .orElseGet(() -> createConversation(tenantId, client.getId(), externalContactId, channel));
  }

  private Cliente resolveOrCreateClient(UUID tenantId, ChatChannel channel, String externalContactId, String contactName) {
    if (channel == ChatChannel.WHATSAPP) {
      return clienteRepository.findByTenantAndPhoneDigits(tenantId, externalContactId)
          .orElseGet(() -> createClientFromInbound(tenantId, externalContactId, contactName));
    }

    ChatConversationEntity existingConversation = chatConversationRepository
        .findByTenantAndExternalContactId(tenantId, externalContactId, channel)
        .orElse(null);
    if (existingConversation != null) {
      Cliente existingClient = clienteRepository.findById(existingConversation.getClientId()).orElse(null);
      if (existingClient != null) return existingClient;
    }
    return createTelegramClientFromInbound(tenantId, externalContactId, contactName);
  }

  /**
   * Corrida: dois webhooks concorrentes para o mesmo cliente novo podem ambos nao encontrar
   * conversa existente e tentar criar. O indice unico uq_chat_conversations_tenant_client_channel
   * (migration V112) garante que so um INSERT vence; a outra transacao recupera a conversa
   * vencedora aqui em vez de propagar erro ou criar duplicata.
   *
   * <p>A TENTATIVA de INSERT roda em REQUIRES_NEW (via {@link #requiresNewTransaction}): a
   * unique violation aborta apenas a transacao aninhada do insert, e o reread do vencedor
   * acontece na transacao externa, limpa.
   */
  private ChatConversationEntity createConversation(
      UUID tenantId, UUID clientId, String externalContactId, ChatChannel channel) {
    ChatChannel resolvedChannel = channel == null ? ChatChannel.WHATSAPP : channel;
    ChatConversationEntity conversation = new ChatConversationEntity();
    conversation.setTenantId(tenantId);
    conversation.setClientId(clientId);
    conversation.setChannel(resolvedChannel);
    conversation.setExternalContactId(externalContactId);
    conversation.setAppointmentMarker(ChatAppointmentMarker.NAO_INICIADO);
    try {
      persistNewConversationInNewTransaction(conversation);
    } catch (DataIntegrityViolationException exception) {
      ChatConversationEntity existing = chatConversationRepository
          .findByTenantClientAndChannel(tenantId, clientId, resolvedChannel)
          .orElse(null);
      if (existing != null) {
        LOG.warn(
            "chat.inbound.conversation.duplicateCreateRace {}",
            CorrelatedLogging.context(
                "tenantId", tenantId,
                "clientId", clientId,
                "channel", resolvedChannel,
                "resolvedConversationId", existing.getId()));
        return existing;
      }
      throw exception;
    }
    auditarChat(
        tenantId,
        "CHAT_CONVERSATION_CREATE",
        AuditConstants.Status.SUCCESS,
        null,
        resumoMarcador(conversation),
        conversation.getId(),
        null,
        null);
    // O insert commitou em transacao propria: recarrega a instancia gerenciada na transacao
    // corrente para que updateConversationPreview e as mutacoes de modo manual sejam
    // dirty-tracked normalmente.
    ChatConversationEntity managed = chatConversationRepository.findById(conversation.getId()).orElse(null);
    return managed != null ? managed : conversation;
  }

  private void persistNewConversationInNewTransaction(ChatConversationEntity conversation) {
    requiresNewTransaction.executeWithoutResult(
        status -> chatConversationRepository.saveAndFlush(conversation));
  }

  OutboundRoute resolveOutboundRoute(UUID tenantId, Cliente client) {
    CustomerCommunicationChannelResolver.ResolvedChannel resolvedChannel =
        customerCommunicationChannelResolver.resolve(tenantId, client, client.getPhone());
    if (resolvedChannel.channel() == ChatChannel.TELEGRAM) {
      return new OutboundRoute(ChatChannel.TELEGRAM, resolvedChannel.externalContactId(), resolvedChannel.conversation());
    }

    String whatsAppDestination = normalizeDigits(resolvedChannel.externalContactId());
    if (whatsAppDestination.length() < 10) {
      throw new IllegalArgumentException("Cliente sem telefone valido para WhatsApp.");
    }
    return new OutboundRoute(ChatChannel.WHATSAPP, whatsAppDestination, resolvedChannel.conversation());
  }

  private void updateConversationPreview(ChatConversationEntity conversation, String content, Instant when) {
    conversation.setLastMessagePreview(truncate(content, 500));
    conversation.setLastMessageAt(when);
    conversation.setUpdatedAt(Instant.now());
  }

  private ChatDtos.Conversation toConversationDtoFromRow(ChatConversationRepository.ConversationWithClientRow r) {
    ChatDtos.Conversation dto = new ChatDtos.Conversation();
    dto.id = r.id() != null ? r.id().toString() : null;
    dto.clientId = r.clientId() != null ? r.clientId().toString() : null;
    dto.clientName = r.clientName();
    dto.clientAvatar = r.clientAvatar();
    dto.clientProfileImageUrl = null;
    dto.clientPhoneMasked = maskPhone(r.clientPhone());
    dto.channel = r.channel();
    dto.appointmentMarker = r.appointmentMarker();
    dto.lastMessageAt = r.lastMessageAt() != null ? r.lastMessageAt().toString() : null;
    dto.lastMessagePreview = r.lastMessagePreview();
    dto.unreadCount = r.unreadCount();
    dto.updatedAt = r.updatedAt() != null ? r.updatedAt().toString() : null;
    dto.manualModeUntil = r.manualModeUntil() != null ? r.manualModeUntil().toString() : null;
    dto.manualModeEnabled = r.manualModeUntil() != null && r.manualModeUntil().isAfter(Instant.now());
    return dto;
  }

  private ChatDtos.Message toMessageDto(ChatMessageEntity entity) {
    ChatDtos.Message dto = new ChatDtos.Message();
    dto.id = entity.getId().toString();
    dto.conversationId = entity.getConversationId() != null ? entity.getConversationId().toString() : null;
    dto.clientId = entity.getClientId() != null ? entity.getClientId().toString() : null;
    dto.direction = entity.getDirection() != null ? entity.getDirection().name() : null;
    dto.content = decryptContent(entity.getContent());
    dto.status = entity.getStatus() != null ? entity.getStatus().name() : null;
    dto.providerMessageId = entity.getProviderMessageId();
    dto.providerErrorCode = entity.getProviderErrorCode();
    dto.providerErrorMessage = entity.getProviderErrorMessage();
    dto.sentAt = entity.getSentAt() != null ? entity.getSentAt().toString() : null;
    dto.deliveredAt = entity.getDeliveredAt() != null ? entity.getDeliveredAt().toString() : null;
    dto.readAt = entity.getReadAt() != null ? entity.getReadAt().toString() : null;
    dto.failedAt = entity.getFailedAt() != null ? entity.getFailedAt().toString() : null;
    dto.createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null;
    return dto;
  }

  private ChatDtos.SendMessageResponse buildSendMessageResponse(ChatMessageEntity message, ChatConversationEntity conversation) {
    ChatDtos.SendMessageResponse response = new ChatDtos.SendMessageResponse();
    response.messageId = message != null && message.getId() != null ? message.getId().toString() : null;
    response.conversationId = conversation != null && conversation.getId() != null ? conversation.getId().toString() : null;
    response.status = message != null && message.getStatus() != null ? message.getStatus().name() : null;
    response.providerErrorMessage = message != null ? message.getProviderErrorMessage() : null;
    return response;
  }

  private long retentionDaysFor(ChatAppointmentMarker marker) {
    if (marker == ChatAppointmentMarker.CONCLUIDO) return Math.max(retentionCompletedDays, 1);
    if (marker == ChatAppointmentMarker.CANCELADO) return Math.max(retentionCanceledDays, 1);
    return Math.max(retentionDefaultDays, 1);
  }

  private UUID parseUuid(String value, String message) {
    try {
      return UUID.fromString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(message);
    }
  }

  private int normalizePage(Integer page) {
    return page == null || page < 1 ? 1 : page;
  }

  private int normalizePageSize(Integer pageSize) {
    int value = pageSize == null || pageSize < 1 ? 20 : pageSize;
    return Math.min(value, 100);
  }

  private String normalize(String value) {
    if (value == null) return null;
    return value.trim();
  }

  private String normalizeDigits(String value) {
    if (value == null) return "";
    return value.replaceAll("\\D", "");
  }

  private String maskPhone(String value) {
    String digits = normalizeDigits(value);
    if (digits.length() < 4) return "***";
    String suffix = digits.substring(digits.length() - 4);
    return "******" + suffix;
  }

  private String truncate(String value, int max) {
    if (value == null) return null;
    if (value.length() <= max) return value;
    return value.substring(0, max);
  }

  private void enableManualMode(ChatConversationEntity conversation, UUID actorUserId, String reason) {
    if (conversation == null) return;
    Instant now = Instant.now();
    conversation.setManualModeUntil(now.plus(Math.max(manualModeDurationHours, 1), ChronoUnit.HOURS));
    conversation.setManualModeByUserId(actorUserId);
    conversation.setManualModeReason(normalize(reason));
    conversation.setUpdatedAt(now);
    auditarChat(
        conversation.getTenantId(),
        "CHAT_MANUAL_MODE_ENABLED",
        AuditConstants.Status.SUCCESS,
        null,
        resumoMarcador(conversation),
        conversation.getId(),
        null,
        null);
    whatsAppBookingReactivationService.cancelCyclesForManualMode(conversation);
  }

  private void clearManualMode(ChatConversationEntity conversation) {
    if (conversation == null) return;
    conversation.setManualModeUntil(null);
    conversation.setManualModeByUserId(null);
    conversation.setManualModeReason(null);
    conversation.setUpdatedAt(Instant.now());
  }

  private boolean isManualModeActive(ChatConversationEntity conversation, Instant now) {
    if (conversation == null || conversation.getManualModeUntil() == null) return false;
    Instant reference = now == null ? Instant.now() : now;
    return conversation.getManualModeUntil().isAfter(reference);
  }

  /**
   * Corrida: dois webhooks concorrentes para o mesmo telefone novo podem ambos passar pelo
   * findByTenantAndPhoneDigits sem encontrar nada e tentar criar o Cliente. O indice unico
   * uq_clients_tenant_phone (migration V112) garante que apenas um INSERT vence.
   *
   * <p>A TENTATIVA de INSERT roda em transacao propria ({@link #requiresNewTransaction}): uma
   * unique violation aborta apenas a transacao aninhada do insert, e o reread do registro
   * vencedor acontece aqui, na transacao externa, que permanece limpa.
   */
  private Cliente createClientFromInbound(UUID tenantId, String phoneDigits, String contactName) {
    Cliente client = new Cliente();
    client.setTenantId(tenantId);
    String candidateName = normalize(contactName);
    String suffix = phoneDigits.length() > 4 ? phoneDigits.substring(phoneDigits.length() - 4) : phoneDigits;
    client.setName(candidateName == null || candidateName.isBlank() ? "Cliente WhatsApp " + suffix : candidateName);
    client.setPhone(phoneDigits);
    try {
      persistNewClientInNewTransaction(client);
    } catch (DataIntegrityViolationException exception) {
      Cliente existing = clienteRepository.findByTenantAndPhoneDigits(tenantId, phoneDigits).orElse(null);
      if (existing != null) {
        LOG.warn(
            "chat.inbound.client.duplicateCreateRace {}",
            CorrelatedLogging.context(
                "tenantId", tenantId,
                "phoneDigits", phoneDigits,
                "resolvedClientId", existing.getId()));
        return existing;
      }
      throw exception;
    }
    // O insert commitou em transacao propria: recarrega a instancia gerenciada para que
    // mutacoes posteriores no fluxo inbound sejam dirty-tracked normalmente.
    Cliente managed = clienteRepository.findById(client.getId()).orElse(null);
    return managed != null ? managed : client;
  }

  private void persistNewClientInNewTransaction(Cliente client) {
    requiresNewTransaction.executeWithoutResult(status -> clienteRepository.saveAndFlush(client));
  }

  private Cliente createTelegramClientFromInbound(UUID tenantId, String chatId, String contactName) {
    Cliente client = new Cliente();
    client.setTenantId(tenantId);
    String candidateName = normalize(contactName);
    String suffix = chatId.length() > 6 ? chatId.substring(chatId.length() - 6) : chatId;
    client.setName(candidateName == null || candidateName.isBlank() ? "Cliente Telegram " + suffix : candidateName);
    client.setPhone(null);
    client.setNotes(appendNote(client.getNotes(), "Origem Telegram chatId=" + chatId));
    clienteRepository.save(client);
    return client;
  }

  private String appendNote(String currentNotes, String newNote) {
    String normalizedCurrent = normalize(currentNotes);
    String normalizedNew = normalize(newNote);
    if (normalizedNew == null) return normalizedCurrent;
    if (normalizedCurrent == null) return normalizedNew;
    return normalizedCurrent + "\n" + normalizedNew;
  }

  private String encryptContent(String plainContent) {
    if (plainContent == null || plainContent.isBlank()) return plainContent;
    return encryptionService.encrypt(plainContent);
  }

  private String decryptContent(String encryptedContent) {
    if (encryptedContent == null || encryptedContent.isBlank()) return encryptedContent;
    try {
      return encryptionService.decrypt(encryptedContent);
    } catch (RuntimeException ignored) {
      return encryptedContent;
    }
  }

  private String safeError(String value) {
    if (value == null || value.isBlank()) return "erro de envio";
    return value.length() > 255 ? value.substring(0, 255) : value;
  }

  private void auditarChat(
      UUID tenantId,
      String action,
      String status,
      Object before,
      Object after,
      UUID entityId,
      String errorCode,
      String errorMessage) {
    try {
      AuditEventCommand command = new AuditEventCommand();
      command.tenantId = tenantId;
      command.actorUserId = obterUsuarioId();
      command.actorRole = obterActorRole();
      command.module = AuditConstants.Module.CHAT;
      command.action = action;
      command.entityType = "CHAT";
      command.entityId = entityId != null ? entityId.toString() : null;
      command.sourceChannel = AuditConstants.SourceChannel.API;
      command.before = before;
      command.after = after;
      command.errorCode = errorCode;
      command.errorMessage = errorMessage;
      if (AuditConstants.Status.ERROR.equals(status)) {
        auditService.recordError(command);
      } else if (AuditConstants.Status.DENIED.equals(status)) {
        auditService.recordDenied(command);
      } else {
        auditService.recordSuccess(command);
      }
    } catch (Exception ignored) {
      // Auditoria nao deve bloquear fluxo principal.
    }
  }

  private Map<String, Object> resumoMensagem(ChatMessageEntity message, String destination) {
    if (message == null) return null;
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("id", message.getId() != null ? message.getId().toString() : null);
    snapshot.put("conversationId", message.getConversationId() != null ? message.getConversationId().toString() : null);
    snapshot.put("clientId", message.getClientId() != null ? message.getClientId().toString() : null);
    snapshot.put("direction", message.getDirection() != null ? message.getDirection().name() : null);
    snapshot.put("status", message.getStatus() != null ? message.getStatus().name() : null);
    snapshot.put("providerMessageId", message.getProviderMessageId());
    snapshot.put("providerErrorMessage", message.getProviderErrorMessage());
    snapshot.put("destination", destination);
    return snapshot;
  }

  private Map<String, Object> resumoMarcador(ChatConversationEntity conversation) {
    if (conversation == null) return null;
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("id", conversation.getId() != null ? conversation.getId().toString() : null);
    snapshot.put("clientId", conversation.getClientId() != null ? conversation.getClientId().toString() : null);
    snapshot.put("channel", conversation.getChannel() != null ? conversation.getChannel().name() : null);
    snapshot.put(
        "appointmentMarker",
        conversation.getAppointmentMarker() != null ? conversation.getAppointmentMarker().name() : null);
    snapshot.put(
        "manualModeUntil",
        conversation.getManualModeUntil() != null ? conversation.getManualModeUntil().toString() : null);
    snapshot.put(
        "manualModeByUserId",
        conversation.getManualModeByUserId() != null ? conversation.getManualModeByUserId().toString() : null);
    snapshot.put("manualModeReason", conversation.getManualModeReason());
    snapshot.put("updatedAt", conversation.getUpdatedAt() != null ? conversation.getUpdatedAt().toString() : null);
    return snapshot;
  }

  private ApiClientErrorException notFound(String message) {
    return new ApiClientErrorException(message, HttpStatus.NOT_FOUND.value());
  }

  public static class InboundProcessingResult {
    public Cliente client;
    public ChatConversationEntity conversation;
    public boolean manualModeEnabled;
    public String normalizedPhone;
    public String externalContactId;
    public boolean duplicateInboundMessage;
  }

  record OutboundRoute(ChatChannel channel, String destination, ChatConversationEntity conversation) {}

  private UUID obterUsuarioId() {
    return authenticatedUser.idOuNulo();
  }

  private String obterActorRole() {
    return authenticatedUser.roleOuNulo();
  }

  private boolean actorCanEnableManualMode() {
    return authenticatedUser.temRole("PROFESSIONAL") || authenticatedUser.temRole("OWNER");
  }
}
