package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantMessageResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantReactivationSeedRequest;
import br.com.phdigitalcode.azzo.agenda.pro.dto.assistant.AssistantStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationAttemptEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppBookingReactivationCycleEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationAttemptStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationAttemptRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppBookingReactivationCycleRepository;

/**
 * Porte completo de {@code modules/chat/application/WhatsAppBookingReactivationService.java}.
 *
 * <p>Fronteira anterior (parcial) portava so {@link #markClientReplyIfNeeded} e
 * {@link #cancelCyclesForManualMode}; esta fronteira completa o restante — criacao/atualizacao de
 * ciclos ({@link #syncAfterAssistantTurn}), agendamento de tentativas
 * ({@link #createAttempt}/{@link #markAttemptSent}/{@link #markAttemptFailed}/
 * {@link #markAttemptCancelled}), conversao ({@link #markConverted}), montagem do payload de
 * retomada para o assistente ({@link #toAssistantSeedRequest}) e do texto de reativacao
 * ({@link #buildOutboundMessage}) — agora que {@code assistantintegration} esta portado e os DTOs
 * {@code AssistantMessageResponse}/{@code AssistantStage}/{@code AssistantReactivationSeedRequest}
 * ja existiam desde a integracao do {@code AssistantApiClient}.
 */
@Service
public class WhatsAppBookingReactivationService {

  private static final ZoneId ZONA_BR = ZoneId.of("America/Sao_Paulo");

  private final WhatsAppBookingReactivationCycleRepository cycleRepository;
  private final WhatsAppBookingReactivationAttemptRepository attemptRepository;
  private final AgendamentoRepository agendamentoRepository;
  private final ObjectMapper objectMapper;
  private final WhatsAppBookingReactivationObservabilityService observabilityService;
  private final int abandonmentDelayMinutes;

  public WhatsAppBookingReactivationService(
      WhatsAppBookingReactivationCycleRepository cycleRepository,
      WhatsAppBookingReactivationAttemptRepository attemptRepository,
      AgendamentoRepository agendamentoRepository,
      ObjectMapper objectMapper,
      WhatsAppBookingReactivationObservabilityService observabilityService,
      @Value("${app.chat.reactivation.abandonment-delay-minutes:30}") int abandonmentDelayMinutes) {
    this.cycleRepository = cycleRepository;
    this.attemptRepository = attemptRepository;
    this.agendamentoRepository = agendamentoRepository;
    this.objectMapper = objectMapper;
    this.observabilityService = observabilityService;
    this.abandonmentDelayMinutes = abandonmentDelayMinutes;
  }

  @Transactional
  public void markClientReplyIfNeeded(UUID tenantId, UUID clientId, String userIdentifier) {
    String normalizedUserIdentifier = normalizeDigits(userIdentifier);
    if (tenantId == null || clientId == null) return;

    Optional<WhatsAppBookingReactivationCycleEntity> cycleOpt = findLatestRelevantCycle(
        tenantId,
        clientId,
        normalizedUserIdentifier,
        List.of(
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStatus.EXHAUSTED));

    if (cycleOpt.isEmpty()) return;

    WhatsAppBookingReactivationCycleEntity cycle = cycleOpt.get();
    if (cycle.getClientId() != null && !clientId.equals(cycle.getClientId())) return;
    if (cycle.getReactivatedAt() != null) return;

    boolean hadSentAttempt = attemptRepository.findLatestSentAttempt(cycle.getId()).isPresent();
    if (!hadSentAttempt) {
      cancelCycle(cycle, "CLIENT_REPLIED_BEFORE_ABANDONMENT");
      cycle.setRespondedAt(Instant.now());
      return;
    }

    Instant now = Instant.now();
    cycle.setStatus(WhatsAppBookingReactivationStatus.REACTIVATED);
    cycle.setReactivatedAt(now);
    cycle.setRespondedAt(now);
    cycle.setNextAttemptAt(null);
    cycle.setCancelReason("CLIENT_REPLIED");
    observabilityService.logClientReply(cycle);
  }

  @Transactional
  public void syncAfterAssistantTurn(
      UUID tenantId,
      ChatConversationEntity conversation,
      Cliente client,
      String inboundMessage,
      AssistantMessageResponse response) {
    if (tenantId == null || conversation == null || client == null || response == null) {
      return;
    }

    String userIdentifier = normalizeDigits(client.getPhone());
    if (userIdentifier.isBlank()) {
      observabilityService.logCycleSkipped(tenantId, null, client.getId(), null, "EMPTY_USER_IDENTIFIER");
      return;
    }

    Optional<WhatsAppBookingReactivationCycleEntity> latestCycleOpt = findLatestRelevantCycle(
        tenantId,
        client.getId(),
        userIdentifier,
        List.of(
            WhatsAppBookingReactivationStatus.ACTIVE,
            WhatsAppBookingReactivationStatus.REACTIVATED,
            WhatsAppBookingReactivationStatus.EXHAUSTED,
            WhatsAppBookingReactivationStatus.CANCELLED));

    if (response.stage == AssistantStage.COMPLETED) {
      if (latestCycleOpt.isEmpty()) {
        observabilityService.logCycleSkipped(
            tenantId,
            conversation.getId(),
            client.getId(),
            response.stage != null ? response.stage.name() : null,
            "COMPLETED_WITHOUT_CYCLE");
      }
      latestCycleOpt.ifPresent(cycle -> {
        UUID appointmentId = parseUuid(slotAsString(response, "appointmentId"));
        if (cycle.getReactivatedAt() != null) {
          markConverted(cycle, appointmentId);
        } else {
          cancelCycle(cycle, "SYSTEM_RULE");
        }
      });
      return;
    }

    if (isIgnoredStage(response.stage)) {
      observabilityService.logCycleSkipped(
          tenantId,
          conversation.getId(),
          client.getId(),
          response.stage != null ? response.stage.name() : null,
          "IGNORED_STAGE");
      return;
    }

    String explicitReactivationStage = slotAsString(response, "reactivationStage");
    WhatsAppBookingReactivationStage stage = mapOperationalStage(explicitReactivationStage);
    if (stage == null) {
      stage = mapAssistantStage(response.stage);
    }
    if (stage == null && isQualifiedBookingLeadWithoutName(response)) {
      stage = WhatsAppBookingReactivationStage.SERVICE_SELECTION;
    }
    if (stage == null) {
      observabilityService.logCycleSkipped(
          tenantId,
          conversation.getId(),
          client.getId(),
          (response.stage != null ? response.stage.name() : null) + "|reactivationStage=" + explicitReactivationStage,
          "UNMAPPED_STAGE");
      return;
    }

    Instant now = Instant.now();
    WhatsAppBookingReactivationCycleEntity cycle = latestCycleOpt.orElse(null);
    boolean shouldReuseCancelledCycle = isReusableCancelledCycle(cycle, now);

    boolean shouldCreateCycle =
        cycle == null
            || (cycle.getStatus() == WhatsAppBookingReactivationStatus.CANCELLED && !shouldReuseCancelledCycle)
            || cycle.getStatus() == WhatsAppBookingReactivationStatus.CONVERTED;

    if (shouldCreateCycle) {
      // Gate LGPD: nao criar ciclo para cliente com opt-out registrado
      if (Boolean.TRUE.equals(client.getWhatsappOptOut())) {
        observabilityService.logCycleSkipped(tenantId, conversation.getId(), client.getId(),
            stage.name(), "OPT_OUT");
        return;
      }
      cycle = new WhatsAppBookingReactivationCycleEntity();
      cycle.setTenantId(tenantId);
      cycle.setClientId(client.getId());
      cycle.setConversationId(conversation.getId());
      cycle.setUserIdentifier(userIdentifier);
      cycle.setCustomerName(safeText(client.getName(), 160));
      cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
    }

    cycle.setConversationId(conversation.getId());
    cycle.setClientId(client.getId());
    cycle.setCustomerName(safeText(client.getName(), 160));
    cycle.setLastStage(stage);
    UUID incomingServiceId = parseUuid(slotAsString(response, "serviceId", "bookingLeadServiceId"));
    String incomingServiceName = safeText(slotAsString(response, "serviceName", "bookingLeadServiceName"), 160);
    UUID incomingProfessionalId = parseUuid(slotAsString(response, "professionalId"));
    String incomingProfessionalName = safeText(slotAsString(response, "professionalName"), 160);
    LocalDate incomingRequestedDate = parseLocalDate(slotAsString(response, "date", "bookingLeadDate"));
    String incomingRequestedTime = safeText(slotAsString(response, "time", "bookingLeadTime"), 10);

    cycle.setLastServiceId(incomingServiceId != null ? incomingServiceId : cycle.getLastServiceId());
    cycle.setLastServiceName(incomingServiceName != null ? incomingServiceName : cycle.getLastServiceName());
    cycle.setLastProfessionalId(
        incomingProfessionalId != null ? incomingProfessionalId : cycle.getLastProfessionalId());
    cycle.setLastProfessionalName(
        incomingProfessionalName != null ? incomingProfessionalName : cycle.getLastProfessionalName());
    cycle.setLastRequestedDate(
        incomingRequestedDate != null ? incomingRequestedDate : cycle.getLastRequestedDate());
    cycle.setLastRequestedTime(
        incomingRequestedTime != null ? incomingRequestedTime : cycle.getLastRequestedTime());
    cycle.setAssistantLastPrompt(safeText(response.reply, 500));
    cycle.setCustomerLastMessage(safeText(inboundMessage, 500));
    cycle.setConversationContextJson(buildContextJson(stage, response, inboundMessage));

    if (cycle.getStatus() == WhatsAppBookingReactivationStatus.ACTIVE
        || cycle.getStatus() == WhatsAppBookingReactivationStatus.EXHAUSTED
        || shouldReuseCancelledCycle) {
      Instant abandonedAt = now.plusSeconds(Math.max(abandonmentDelayMinutes, 1) * 60L);
      cycle.setStatus(WhatsAppBookingReactivationStatus.ACTIVE);
      cycle.setAbandonedAt(abandonedAt);
      cycle.setNextAttemptNumber(1);
      cycle.setNextAttemptAt(abandonedAt.plusSeconds(2L * 24L * 60L * 60L));
      cycle.setCancelReason(null);
      cycle.setReactivatedAt(null);
      cycle.setRespondedAt(null);
      cycle.setConvertedAt(null);
      cycle.setAppointmentIdCreatedAfterAbandonment(null);
    }

    if (shouldCreateCycle) {
      cycleRepository.save(cycle);
      observabilityService.logCycleCreated(cycle);
    } else {
      observabilityService.logCycleUpdated(cycle, stage);
    }
  }

  public void cancelCyclesForManualMode(ChatConversationEntity conversation) {
    if (conversation == null || conversation.getTenantId() == null || conversation.getClientId() == null) return;
    List<WhatsAppBookingReactivationCycleEntity> cycles =
        cycleRepository.listActiveByTenantAndClient(conversation.getTenantId(), conversation.getClientId());
    for (WhatsAppBookingReactivationCycleEntity cycle : cycles) {
      cancelCycle(cycle, "MANUAL_MODE");
    }
  }

  public List<WhatsAppBookingReactivationCycleEntity> listDueCycles(Instant now) {
    return cycleRepository.listDue(now == null ? Instant.now() : now);
  }

  @Transactional(readOnly = true)
  public Optional<Agendamento> findFutureActiveAppointment(UUID tenantId, UUID clientId) {
    LocalDate today = LocalDate.now(ZONA_BR);
    String currentTime = LocalTime.now(ZONA_BR).withSecond(0).withNano(0).toString();
    return agendamentoRepository.findFirstFutureActiveForClient(tenantId, clientId, today, currentTime);
  }

  @Transactional
  public WhatsAppBookingReactivationAttemptEntity createAttempt(
      WhatsAppBookingReactivationCycleEntity cycle,
      Instant scheduledFor) {
    WhatsAppBookingReactivationAttemptEntity attempt = new WhatsAppBookingReactivationAttemptEntity();
    attempt.setCycleId(cycle.getId());
    attempt.setTenantId(cycle.getTenantId());
    attempt.setAttemptNumber(cycle.getNextAttemptNumber());
    attempt.setScheduledFor(scheduledFor == null ? Instant.now() : scheduledFor);
    attempt.setStatus(WhatsAppBookingReactivationAttemptStatus.PENDING);
    return attemptRepository.save(attempt);
  }

  @Transactional
  public void markAttemptSent(
      WhatsAppBookingReactivationCycleEntity cycle,
      WhatsAppBookingReactivationAttemptEntity attempt,
      String providerMessageId) {
    Instant now = Instant.now();
    attempt.setStatus(WhatsAppBookingReactivationAttemptStatus.SENT);
    attempt.setSentAt(now);
    attempt.setProviderMessageId(safeText(providerMessageId, 120));

    advanceCycleAfterAttempt(cycle);
    observabilityService.logAttemptSent(cycle, attempt);
  }

  @Transactional
  public void markAttemptFailed(
      WhatsAppBookingReactivationCycleEntity cycle,
      WhatsAppBookingReactivationAttemptEntity attempt,
      String errorMessage) {
    attempt.setStatus(WhatsAppBookingReactivationAttemptStatus.FAILED);
    attempt.setErrorMessage(safeText(errorMessage, 255));

    advanceCycleAfterAttempt(cycle);
    observabilityService.logAttemptFailed(cycle, attempt, errorMessage);
  }

  private void advanceCycleAfterAttempt(WhatsAppBookingReactivationCycleEntity cycle) {
    if (cycle.getNextAttemptNumber() >= 3) {
      cycle.setStatus(WhatsAppBookingReactivationStatus.EXHAUSTED);
      cycle.setNextAttemptNumber(4);
      cycle.setNextAttemptAt(null);
    } else if (cycle.getNextAttemptNumber() == 1) {
      cycle.setNextAttemptNumber(2);
      cycle.setNextAttemptAt(cycle.getAbandonedAt().plusSeconds(4L * 24L * 60L * 60L));
    } else {
      cycle.setNextAttemptNumber(3);
      cycle.setNextAttemptAt(cycle.getAbandonedAt().plusSeconds(7L * 24L * 60L * 60L));
    }
  }

  @Transactional
  public void markAttemptCancelled(
      WhatsAppBookingReactivationCycleEntity cycle,
      WhatsAppBookingReactivationAttemptEntity attempt,
      String reason) {
    if (attempt != null) {
      attempt.setStatus(WhatsAppBookingReactivationAttemptStatus.CANCELLED);
      attempt.setErrorMessage(safeText(reason, 255));
    }
    if (cycle != null) {
      cycle.setStatus(WhatsAppBookingReactivationStatus.CANCELLED);
      cycle.setCancelReason(safeText(reason, 60));
      cycle.setNextAttemptAt(null);
    }
    observabilityService.logAttemptCancelled(cycle, attempt, reason);
    observabilityService.logCycleCancelled(cycle, reason);
  }

  @Transactional
  public void markConverted(WhatsAppBookingReactivationCycleEntity cycle, UUID appointmentId) {
    if (cycle == null) return;
    cycle.setStatus(WhatsAppBookingReactivationStatus.CONVERTED);
    cycle.setConvertedAt(Instant.now());
    cycle.setAppointmentIdCreatedAfterAbandonment(appointmentId);
    cycle.setNextAttemptAt(null);
    cycle.setCancelReason(null);
    observabilityService.logCycleConverted(cycle);
  }

  @Transactional
  public void cancelCycle(WhatsAppBookingReactivationCycleEntity cycle, String reason) {
    if (cycle == null) return;
    cycle.setStatus(WhatsAppBookingReactivationStatus.CANCELLED);
    cycle.setCancelReason(safeText(reason, 60));
    cycle.setNextAttemptAt(null);
    observabilityService.logCycleCancelled(cycle, reason);
  }

  public AssistantReactivationSeedRequest toAssistantSeedRequest(WhatsAppBookingReactivationCycleEntity cycle) {
    AssistantReactivationSeedRequest request = new AssistantReactivationSeedRequest();
    request.cycleId = cycle.getId() != null ? cycle.getId().toString() : null;
    request.customerName = cycle.getCustomerName();
    request.resumeStage = mapResumeStage(cycle.getLastStage());
    request.serviceId = cycle.getLastServiceId() != null ? cycle.getLastServiceId().toString() : null;
    request.serviceName = cycle.getLastServiceName();
    request.professionalId =
        cycle.getLastProfessionalId() != null ? cycle.getLastProfessionalId().toString() : null;
    request.professionalName = cycle.getLastProfessionalName();
    request.date = cycle.getLastRequestedDate() != null ? cycle.getLastRequestedDate().toString() : null;
    request.time = cycle.getLastRequestedTime();
    request.assistantLastPrompt = cycle.getAssistantLastPrompt();
    return request;
  }

  public String buildOutboundMessage(WhatsAppBookingReactivationCycleEntity cycle, int attemptNumber) {
    return buildOutboundMessage(cycle, attemptNumber, null, null, null);
  }

  public String buildOutboundMessage(
      WhatsAppBookingReactivationCycleEntity cycle,
      int attemptNumber,
      String templateAttempt1,
      String templateAttempt2,
      String templateAttempt3) {
    // Usar template configurado pelo tenant se disponivel
    String template = switch (attemptNumber) {
      case 1 -> templateAttempt1;
      case 2 -> templateAttempt2;
      default -> templateAttempt3;
    };
    if (template != null && !template.isBlank()) {
      // Substituir variavel de primeiro nome
      String firstName = firstNameOf(cycle.getCustomerName());
      return template.replace("{nome}", firstName).replace("{name}", firstName);
    }

    String nome = cycle.getCustomerName() != null && !cycle.getCustomerName().isBlank()
        ? ", " + cycle.getCustomerName()
        : "";
    String contexto = switch (cycle.getLastStage()) {
      case SERVICE_SELECTION -> "seu agendamento ficou parado logo na escolha do servico";
      case PROFESSIONAL_SELECTION -> cycle.getLastServiceName() != null && !cycle.getLastServiceName().isBlank()
          ? "voce estava escolhendo o profissional para " + cycle.getLastServiceName()
          : "seu agendamento ficou parado na escolha do profissional";
      case TIME_SELECTION -> cycle.getLastServiceName() != null && !cycle.getLastServiceName().isBlank()
          ? "faltou concluir o horario do seu " + cycle.getLastServiceName()
          : "faltou concluir o horario do seu agendamento";
      case FINAL_REVIEW -> "faltou so confirmar o seu agendamento";
    };

    return switch (attemptNumber) {
      case 1 -> "Oi" + nome + "! Notei que " + contexto + ". Se quiser, posso continuar de onde paramos aqui mesmo. 😊";
      case 2 -> "Oi" + nome + "! Passando para lembrar que " + contexto + ". Se quiser finalizar agora, me responde por aqui. 💬";
      default -> "Oi" + nome + "! Essa e minha ultima mensagem sobre aquele agendamento que ficou pela metade. Se quiser retomar, e so me responder aqui. ✨";
    };
  }

  private String buildContextJson(
      WhatsAppBookingReactivationStage stage,
      AssistantMessageResponse response,
      String inboundMessage) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("stage", stage != null ? stage.name() : null);
    context.put("serviceId", slotAsString(response, "serviceId", "bookingLeadServiceId"));
    context.put("serviceName", slotAsString(response, "serviceName", "bookingLeadServiceName"));
    context.put("professionalId", slotAsString(response, "professionalId"));
    context.put("professionalName", slotAsString(response, "professionalName"));
    context.put("requestedDate", slotAsString(response, "date", "bookingLeadDate"));
    context.put("requestedTime", slotAsString(response, "time", "bookingLeadTime"));
    context.put("assistantLastPrompt", safeText(response.reply, 500));
    context.put("customerLastMessage", safeText(inboundMessage, 500));
    context.put("manualInterventionSuggested", slotAsBoolean(response, "manualInterventionSuggested"));
    context.put("manualInterventionReason", slotAsString(response, "manualInterventionReason"));
    context.put("manualInterventionAttempts", slotAsInteger(response, "manualInterventionAttempts"));
    try {
      return objectMapper.writeValueAsString(context);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private String slotAsString(AssistantMessageResponse response, String... keys) {
    if (response == null || response.slots == null || keys == null) return null;
    for (String key : keys) {
      if (key == null) continue;
      Object value = response.slots.get(key);
      if (value == null) continue;
      String text = value.toString();
      if (text != null && !text.isBlank()) return text;
    }
    return null;
  }

  private Boolean slotAsBoolean(AssistantMessageResponse response, String... keys) {
    String value = slotAsString(response, keys);
    if (value == null || value.isBlank()) return null;
    return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
  }

  private Integer slotAsInteger(AssistantMessageResponse response, String... keys) {
    String value = slotAsString(response, keys);
    if (value == null || value.isBlank()) return null;
    try {
      return Integer.valueOf(value.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private UUID parseUuid(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return UUID.fromString(value);
    } catch (Exception ignored) {
      return null;
    }
  }

  private LocalDate parseLocalDate(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDate.parse(value);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String normalizeDigits(String value) {
    if (value == null) return "";
    return value.replaceAll("\\D", "");
  }

  private boolean isReusableCancelledCycle(WhatsAppBookingReactivationCycleEntity cycle, Instant now) {
    if (cycle == null || cycle.getStatus() != WhatsAppBookingReactivationStatus.CANCELLED) return false;
    Instant reference = mostRecentInstant(
        cycle.getUpdatedAt(), cycle.getRespondedAt(), cycle.getAbandonedAt(), cycle.getCreatedAt());
    if (reference == null) return false;
    Instant cutoff = now.minusSeconds(Math.max(abandonmentDelayMinutes, 1) * 60L);
    return !reference.isBefore(cutoff);
  }

  private Instant mostRecentInstant(Instant... candidates) {
    if (candidates == null || candidates.length == 0) return null;
    Instant latest = null;
    for (Instant candidate : candidates) {
      if (candidate == null) continue;
      if (latest == null || candidate.isAfter(latest)) latest = candidate;
    }
    return latest;
  }

  private Optional<WhatsAppBookingReactivationCycleEntity> findLatestRelevantCycle(
      UUID tenantId,
      UUID clientId,
      String userIdentifier,
      List<WhatsAppBookingReactivationStatus> statuses) {
    Optional<WhatsAppBookingReactivationCycleEntity> byClient =
        cycleRepository.findLatestByTenantAndClient(tenantId, clientId, statuses);
    if (byClient.isPresent()) {
      return byClient;
    }
    List<String> identifiers = buildUserIdentifierVariants(userIdentifier);
    if (identifiers.isEmpty()) {
      return Optional.empty();
    }
    return cycleRepository.findLatestByTenantAndUserIdentifiers(tenantId, identifiers, statuses);
  }

  private List<String> buildUserIdentifierVariants(String userIdentifier) {
    String normalized = normalizeDigits(userIdentifier);
    if (normalized.isBlank()) {
      return List.of();
    }

    LinkedHashSet<String> variants = new LinkedHashSet<>();
    variants.add(normalized);

    if (normalized.startsWith("55") && normalized.length() > 11) {
      variants.add(normalized.substring(2));
    } else if (normalized.length() == 10 || normalized.length() == 11) {
      variants.add("55" + normalized);
    }

    return new ArrayList<>(variants);
  }

  public String maskUserIdentifier(String value) {
    return observabilityService.maskUserIdentifier(value);
  }

  private String safeText(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    if (normalized.isBlank()) return null;
    return normalized.length() > max ? normalized.substring(0, max) : normalized;
  }

  private boolean isIgnoredStage(AssistantStage stage) {
    if (stage == null) return false;
    return stage == AssistantStage.ASK_CANCEL_APPOINTMENT
        || stage == AssistantStage.ASK_RESCHEDULE_APPOINTMENT
        || stage == AssistantStage.AWAITING_APPOINTMENT_CONFIRMATION
        || stage == AssistantStage.AWAITING_REACTIVATION_REPLY;
  }

  private boolean isQualifiedBookingLeadWithoutName(AssistantMessageResponse response) {
    if (response == null || response.slots == null) return false;
    Object value = response.slots.get("bookingLeadDetected");
    if (value == null) return false;
    String normalized = value.toString().trim();
    return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
  }

  private WhatsAppBookingReactivationStage mapAssistantStage(AssistantStage stage) {
    if (stage == null) return null;
    return switch (stage) {
      case START, ASK_NAME, ASK_SERVICE -> WhatsAppBookingReactivationStage.SERVICE_SELECTION;
      case ASK_PROFESSIONAL -> WhatsAppBookingReactivationStage.PROFESSIONAL_SELECTION;
      case ASK_DATE, ASK_PERIOD, ASK_TIME -> WhatsAppBookingReactivationStage.TIME_SELECTION;
      case CONFIRMATION -> WhatsAppBookingReactivationStage.FINAL_REVIEW;
      default -> null;
    };
  }

  private WhatsAppBookingReactivationStage mapOperationalStage(String stage) {
    if (stage == null || stage.isBlank()) return null;
    return switch (stage.trim().toUpperCase()) {
      case "SERVICE_SELECTION" -> WhatsAppBookingReactivationStage.SERVICE_SELECTION;
      case "PROFESSIONAL_SELECTION" -> WhatsAppBookingReactivationStage.PROFESSIONAL_SELECTION;
      case "TIME_SELECTION" -> WhatsAppBookingReactivationStage.TIME_SELECTION;
      case "FINAL_REVIEW" -> WhatsAppBookingReactivationStage.FINAL_REVIEW;
      default -> null;
    };
  }

  private String firstNameOf(String fullName) {
    if (fullName == null || fullName.isBlank()) return "";
    String trimmed = fullName.trim();
    int space = trimmed.indexOf(' ');
    return space > 0 ? trimmed.substring(0, space) : trimmed;
  }

  private String mapResumeStage(WhatsAppBookingReactivationStage stage) {
    if (stage == null) return "ASK_SERVICE";
    return switch (stage) {
      case SERVICE_SELECTION -> "ASK_SERVICE";
      case PROFESSIONAL_SELECTION -> "ASK_PROFESSIONAL";
      case TIME_SELECTION -> "ASK_TIME";
      case FINAL_REVIEW -> "CONFIRMATION";
    };
  }
}
