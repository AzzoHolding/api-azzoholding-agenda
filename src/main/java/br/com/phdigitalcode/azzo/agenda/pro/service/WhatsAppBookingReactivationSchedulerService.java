package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantReactivationSeedRequest;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ReactivationSendLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantReactivationConfigEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ReactivationSendLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantReactivationConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Espelha {@code modules/chat/application/WhatsAppBookingReactivationSchedulerService.java} (325
 * linhas no original).
 *
 * <p>{@code TraceContext.traceId()} (Skywalking, agente Java carregado externamente no Quarkus,
 * sem equivalente configurado neste projeto Spring) foi substituido nos logs por
 * {@code MDC.get("traceId")} — mesma ideia (id de correlacao no log), mecanismo diferente,
 * seguindo o padrao ja usado por {@code CorrelatedLogging} no restante do projeto portado.
 */
@Service
public class WhatsAppBookingReactivationSchedulerService {

  private static final Logger LOG = LoggerFactory.getLogger(WhatsAppBookingReactivationSchedulerService.class);
  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  private final WhatsAppBookingReactivationService whatsAppBookingReactivationService;
  private final WhatsAppBookingReactivationCycleRepository cycleRepository;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private final ClienteRepository clienteRepository;
  private final ChatConversationRepository chatConversationRepository;
  private final CommunicationChannelDispatcher communicationChannelDispatcher;
  private final CustomerCommunicationChannelResolver customerCommunicationChannelResolver;
  private final ChatService chatService;
  private final TenantOperationalSettingsService tenantOperationalSettingsService;
  private final TenantReactivationConfigRepository tenantReactivationConfigRepository;
  private final ReactivationSendLogRepository reactivationSendLogRepository;
  private final AssistantApiClient assistantApiClient;

  public WhatsAppBookingReactivationSchedulerService(
      WhatsAppBookingReactivationService whatsAppBookingReactivationService,
      WhatsAppBookingReactivationCycleRepository cycleRepository,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      TenantTelegramConfigRepository tenantTelegramConfigRepository,
      ClienteRepository clienteRepository,
      ChatConversationRepository chatConversationRepository,
      CommunicationChannelDispatcher communicationChannelDispatcher,
      CustomerCommunicationChannelResolver customerCommunicationChannelResolver,
      ChatService chatService,
      TenantOperationalSettingsService tenantOperationalSettingsService,
      TenantReactivationConfigRepository tenantReactivationConfigRepository,
      ReactivationSendLogRepository reactivationSendLogRepository,
      AssistantApiClient assistantApiClient) {
    this.whatsAppBookingReactivationService = whatsAppBookingReactivationService;
    this.cycleRepository = cycleRepository;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.tenantTelegramConfigRepository = tenantTelegramConfigRepository;
    this.clienteRepository = clienteRepository;
    this.chatConversationRepository = chatConversationRepository;
    this.communicationChannelDispatcher = communicationChannelDispatcher;
    this.customerCommunicationChannelResolver = customerCommunicationChannelResolver;
    this.chatService = chatService;
    this.tenantOperationalSettingsService = tenantOperationalSettingsService;
    this.tenantReactivationConfigRepository = tenantReactivationConfigRepository;
    this.reactivationSendLogRepository = reactivationSendLogRepository;
    this.assistantApiClient = assistantApiClient;
  }

  public int processDueCycles() {
    List<UUID> cycleIds =
        whatsAppBookingReactivationService.listDueCycles(Instant.now()).stream()
            .map(WhatsAppBookingReactivationCycleEntity::getId)
            .filter(id -> id != null)
            .toList();
    LOG.info("WhatsAppBookingReactivation iniciado. cycles={}", cycleIds.size());
    int processed = 0;
    for (UUID cycleId : cycleIds) {
      processed += processCycle(cycleId);
    }
    LOG.info("WhatsAppBookingReactivation finalizado. cyclesProcessados={}", processed);
    return processed;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int processCycle(UUID cycleId) {
    WhatsAppBookingReactivationCycleEntity cycle = cycleRepository.findById(cycleId).orElse(null);
    if (cycle == null || cycle.getTenantId() == null) return 0;
    Instant now = Instant.now();

    if (!tenantOperationalSettingsService.isReactivationEnabled(cycle.getTenantId())) {
      whatsAppBookingReactivationService.cancelCycle(cycle, "REACTIVATION_DISABLED");
      return 1;
    }

    if (!tenantOperationalSettingsService.allowsReactivationAttemptNumber(cycle.getTenantId(), cycle.getNextAttemptNumber())) {
      return 0;
    }
    if (!tenantOperationalSettingsService.allowsReactivationAt(cycle.getTenantId(), now)) {
      return 0;
    }

    Cliente client =
        cycle.getClientId() == null
            ? null
            : clienteRepository.findByIdAndTenantId(cycle.getClientId(), cycle.getTenantId()).orElse(null);
    if (client == null) {
      whatsAppBookingReactivationService.cancelCycle(cycle, "INVALID_DESTINATION");
      return 1;
    }

    ChatConversationEntity conversation =
        cycle.getConversationId() == null
            ? null
            : chatConversationRepository.findByTenantAndId(cycle.getTenantId(), cycle.getConversationId()).orElse(null);
    if (conversation != null
        && conversation.getManualModeUntil() != null
        && conversation.getManualModeUntil().isAfter(now)) {
      whatsAppBookingReactivationService.cancelCycle(cycle, "MANUAL_MODE");
      return 1;
    }

    ReactivationRoute route = resolveReactivationRoute(cycle.getTenantId(), client, cycle, conversation);
    if (!isChannelEnabledForReactivation(cycle.getTenantId(), route.channel())) {
      whatsAppBookingReactivationService.cancelCycle(
          cycle, route.channel() == ChatChannel.TELEGRAM ? "TELEGRAM_DISABLED" : "WHATSAPP_DISABLED");
      return 1;
    }
    if (route.channel() == ChatChannel.WHATSAPP && !isWhatsAppReactivationAllowed(cycle.getTenantId())) {
      whatsAppBookingReactivationService.cancelCycle(cycle, "PROFILE_REACTIVATION_DISABLED");
      return 1;
    }

    // Gate LGPD 1: opt-out atual somente para WhatsApp
    if (route.channel() == ChatChannel.WHATSAPP && Boolean.TRUE.equals(client.getWhatsappOptOut())) {
      whatsAppBookingReactivationService.cancelCycle(cycle, "OPT_OUT");
      return 1;
    }

    // Gate LGPD 2: menoridade
    if (client.getBirthDate() != null && Period.between(client.getBirthDate(), LocalDate.now()).getYears() < 18) {
      whatsAppBookingReactivationService.cancelCycle(cycle, "MINOR");
      return 1;
    }

    TenantReactivationConfigEntity reactivationConfig =
        tenantReactivationConfigRepository.findByTenantIdOrDefault(cycle.getTenantId());

    int maxPerMonth = Math.min(reactivationConfig.getMaxMessagesPerMonthPerClient(), 4);
    Instant inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZONA_BR).toInstant();
    long sentThisMonth = reactivationSendLogRepository.countByClientIdAndSentAtAfter(client.getId(), inicioMes);
    if (sentThisMonth >= maxPerMonth) {
      LOG.debug(
          "Reativacao: rate limit mensal atingido cycleId={} tentativa={} tenantId={}",
          cycleId, cycle.getNextAttemptNumber(), cycle.getTenantId());
      return 0;
    }

    int minInterval = Math.max(reactivationConfig.getMinIntervalDays(), 7);
    Optional<ReactivationSendLogEntity> lastSent = reactivationSendLogRepository.findLastByClientId(client.getId());
    if (lastSent.isPresent()
        && lastSent.get().getSentAt().isAfter(Instant.now().minusSeconds((long) minInterval * 24 * 60 * 60))) {
      LOG.debug("Reativacao: intervalo minimo nao atingido cycleId={} tenantId={}", cycleId, cycle.getTenantId());
      return 0;
    }

    ZonedDateTime nowInZone = ZonedDateTime.now(ZONA_BR);
    LocalTime currentTime = nowInZone.toLocalTime();
    if (currentTime.isBefore(reactivationConfig.getSendWindowStart())
        || currentTime.isAfter(reactivationConfig.getSendWindowEnd())) {
      LOG.debug("Reativacao: fora da janela de horario cycleId={} tenantId={}", cycleId, cycle.getTenantId());
      return 0;
    }

    if (whatsAppBookingReactivationService.findFutureActiveAppointment(cycle.getTenantId(), client.getId()).isPresent()) {
      if (cycle.getReactivatedAt() != null) {
        whatsAppBookingReactivationService.markConverted(cycle, null);
      } else {
        whatsAppBookingReactivationService.cancelCycle(cycle, "FUTURE_APPOINTMENT_FOUND");
      }
      return 1;
    }

    Instant scheduledFor = cycle.getNextAttemptAt() != null ? cycle.getNextAttemptAt() : now;
    WhatsAppBookingReactivationAttemptEntity attempt =
        whatsAppBookingReactivationService.createAttempt(cycle, scheduledFor);

    String outboundMessage =
        whatsAppBookingReactivationService.buildOutboundMessage(
            cycle,
            attempt.getAttemptNumber(),
            reactivationConfig.getTemplateAttempt1(),
            reactivationConfig.getTemplateAttempt2(),
            reactivationConfig.getTemplateAttempt3());

    try {
      ChannelSendResult sendResult =
          communicationChannelDispatcher.sendText(
              new ChannelSendCommand(cycle.getTenantId(), route.channel(), route.destination(), outboundMessage));
      if (!sendResult.success()) {
        throw new IllegalStateException(
            firstNonBlank(
                sendResult.providerErrorMessage(), sendResult.providerErrorCode(), "Falha ao enviar reativacao"));
      }
      String providerMessageId = sendResult.providerMessageId();
      whatsAppBookingReactivationService.markAttemptSent(cycle, attempt, providerMessageId);

      ReactivationSendLogEntity sendLog = new ReactivationSendLogEntity();
      sendLog.setTenantId(cycle.getTenantId());
      sendLog.setClientId(client.getId());
      sendLog.setCycleId(cycle.getId());
      sendLog.setAttemptNumber(attempt.getAttemptNumber());
      sendLog.setStatus("SENT");
      sendLog.setProviderMessageId(
          providerMessageId != null && providerMessageId.length() <= 255 ? providerMessageId : null);
      reactivationSendLogRepository.save(sendLog);
      LOG.info(
          "Reativacao enviada: cycleId={} tentativa={} tenantId={} canal={}",
          cycleId, attempt.getAttemptNumber(), cycle.getTenantId(), route.channel());

      if (route.conversation() != null && client.getId() != null) {
        chatService.registerOutboundAssistantMessage(
            cycle.getTenantId(),
            route.conversation().getId(),
            client.getId(),
            outboundMessage,
            providerMessageId,
            ChatMessageStatus.SENT,
            null,
            null);
      }

      AssistantReactivationSeedRequest seedRequest = whatsAppBookingReactivationService.toAssistantSeedRequest(cycle);
      assistantApiClient.seedReactivationContext(cycle.getTenantId().toString(), route.destination(), seedRequest);
    } catch (RuntimeException e) {
      String errorMessage = sanitizeError(e.getMessage());
      if (isInvalidDestinationError(errorMessage, route.channel())) {
        LOG.warn("reactivation_invalid_destination cycleId={} tenantId={}", cycleId, cycle.getTenantId());
        whatsAppBookingReactivationService.markAttemptCancelled(cycle, attempt, "INVALID_DESTINATION");
      } else {
        LOG.error("reactivation_send_failed cycleId={} tenantId={}", cycleId, cycle.getTenantId(), e);
        whatsAppBookingReactivationService.markAttemptFailed(cycle, attempt, errorMessage);
      }
    }
    return 1;
  }

  ReactivationRoute resolveReactivationRoute(
      UUID tenantId, Cliente client, WhatsAppBookingReactivationCycleEntity cycle, ChatConversationEntity currentConversation) {
    CustomerCommunicationChannelResolver.ResolvedChannel resolvedChannel =
        customerCommunicationChannelResolver.resolve(
            tenantId, client, currentConversation, client.getPhone(), cycle != null ? cycle.getUserIdentifier() : null);
    if (resolvedChannel.channel() == ChatChannel.TELEGRAM) {
      return new ReactivationRoute(ChatChannel.TELEGRAM, resolvedChannel.externalContactId(), resolvedChannel.conversation());
    }

    String whatsAppDestination = normalizeDigits(resolvedChannel.externalContactId());
    if (whatsAppDestination.length() < 10) {
      throw new IllegalArgumentException("Cliente sem telefone valido para reativacao via WhatsApp.");
    }
    return new ReactivationRoute(ChatChannel.WHATSAPP, whatsAppDestination, resolvedChannel.conversation());
  }

  private boolean isChannelEnabledForReactivation(UUID tenantId, ChatChannel channel) {
    if (channel == ChatChannel.TELEGRAM) {
      TenantTelegramConfig config = tenantTelegramConfigRepository.findByTenantId(tenantId);
      return config != null && config.isTelegramEnabled();
    }
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return config != null && config.isWhatsappEnabled();
  }

  private boolean isWhatsAppReactivationAllowed(UUID tenantId) {
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return config != null && config.isReactivationAllowed();
  }

  private String normalizeDigits(String value) {
    if (value == null) return "";
    return value.replaceAll("\\D", "");
  }

  private String sanitizeError(String message) {
    if (message == null || message.isBlank()) return "erro nao especificado";
    return message.length() > 255 ? message.substring(0, 255) : message;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private boolean isInvalidDestinationError(String message, ChatChannel channel) {
    if (message == null || message.isBlank()) return false;
    String normalized = message.toLowerCase();
    if (channel == ChatChannel.TELEGRAM) {
      return normalized.contains("destino do telegram invalido")
          || normalized.contains("chat not found")
          || normalized.contains("bad request")
          || normalized.contains("chat_id");
    }
    return normalized.contains("destino do whatsapp invalido")
        || normalized.contains("invalid recipient")
        || normalized.contains("recipient phone number")
        || normalized.contains("invalid parameter")
        || normalized.contains("phone number");
  }

  record ReactivationRoute(ChatChannel channel, String destination, ChatConversationEntity conversation) {}
}
