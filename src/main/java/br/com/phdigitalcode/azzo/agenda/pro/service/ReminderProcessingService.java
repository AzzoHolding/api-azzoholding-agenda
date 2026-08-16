package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantOperationalSettings;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusAgendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.integration.AssistantApiClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantOperationalSettingsRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendCommand;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.CommunicationChannelDispatcher;

/**
 * Espelha {@code modules/scheduling/application/ReminderProcessingService.java} (F03 — regua de
 * lembretes): lembrete na vespera (D-1, so dispara apos o horario configurado por tenant) e
 * lembrete N horas antes do horario marcado, cada um em sua propria janela de disparo.
 *
 * <p><b>Isolamento por item.</b> O original abre {@code @Transactional(REQUIRES_NEW)} em
 * {@code processAppointment}/{@code processHoursBeforeAppointment} para que a falha de um
 * agendamento nao derrube o lote inteiro. Chamar um metodo {@code @Transactional} da propria
 * classe nao passa pelo proxy do Spring, entao a nova transacao e aberta explicitamente com
 * {@link TransactionTemplate} — mesmo padrao ja usado em
 * {@code AppointmentNoShowProcessingService}.
 */
@Service
public class ReminderProcessingService {

  private static final Logger LOG = LoggerFactory.getLogger(ReminderProcessingService.class);
  private static final ZoneId ZONE_BR = ZoneId.of("America/Sao_Paulo");
  private static final String CHANNEL_WHATSAPP_REMINDER = "WHATSAPP_REMINDER";
  private static final String CHANNEL_TELEGRAM_REMINDER = "TELEGRAM_REMINDER";
  private static final String CHANNEL_WHATSAPP_REMINDER_HOURS = "WHATSAPP_REMINDER_HOURS";
  private static final String CHANNEL_TELEGRAM_REMINDER_HOURS = "TELEGRAM_REMINDER_HOURS";
  private static final int MAX_HOURS_BEFORE = 12;

  private final AgendamentoRepository agendamentoRepository;
  private final TenantRepository tenantRepository;
  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private final TenantOperationalSettingsRepository tenantOperationalSettingsRepository;
  private final ClienteRepository clienteRepository;
  private final NotificationRepository notificationRepository;
  private final NotificationPublisher notificationPublisher;
  private final CommunicationChannelDispatcher communicationChannelDispatcher;
  private final CustomerCommunicationChannelResolver customerCommunicationChannelResolver;
  private final AssistantApiClient assistantApiClient;
  private final TransactionTemplate requiresNewTransaction;

  public ReminderProcessingService(
      AgendamentoRepository agendamentoRepository,
      TenantRepository tenantRepository,
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository,
      TenantTelegramConfigRepository tenantTelegramConfigRepository,
      TenantOperationalSettingsRepository tenantOperationalSettingsRepository,
      ClienteRepository clienteRepository,
      NotificationRepository notificationRepository,
      NotificationPublisher notificationPublisher,
      CommunicationChannelDispatcher communicationChannelDispatcher,
      CustomerCommunicationChannelResolver customerCommunicationChannelResolver,
      AssistantApiClient assistantApiClient,
      PlatformTransactionManager transactionManager) {
    this.agendamentoRepository = agendamentoRepository;
    this.tenantRepository = tenantRepository;
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.tenantTelegramConfigRepository = tenantTelegramConfigRepository;
    this.tenantOperationalSettingsRepository = tenantOperationalSettingsRepository;
    this.clienteRepository = clienteRepository;
    this.notificationRepository = notificationRepository;
    this.notificationPublisher = notificationPublisher;
    this.communicationChannelDispatcher = communicationChannelDispatcher;
    this.customerCommunicationChannelResolver = customerCommunicationChannelResolver;
    this.assistantApiClient = assistantApiClient;
    this.requiresNewTransaction = new TransactionTemplate(transactionManager);
    this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Lembrete na vespera (F03 — regua de lembretes): so dispara apos o horario configurado (d1Hora) por tenant. */
  public int sendReminders() {
    LocalDate targetDate = LocalDate.now().plusDays(1);
    List<UUID> appointmentIds = agendamentoRepository.listIdsByDateAndStatus(
        targetDate, List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED));
    LOG.info("ReminderProcessing (D-1) iniciado. targetDate={} appointments={}", targetDate, appointmentIds.size());
    int processed = 0;
    for (UUID appointmentId : appointmentIds) {
      processed += processAppointment(appointmentId);
    }
    LOG.info("ReminderProcessing (D-1) finalizado. processed={}", processed);
    return processed;
  }

  /** Lembrete N horas antes do horario marcado (F03), independente do lembrete de vespera. */
  public int sendHoursBeforeReminders() {
    LocalDate today = LocalDate.now(ZONE_BR);
    LocalDate tomorrow = today.plusDays(1);
    List<UUID> appointmentIds = agendamentoRepository.listIdsByDatesInAndStatus(
        List.of(today, tomorrow), List.of(StatusAgendamento.PENDING, StatusAgendamento.CONFIRMED));
    LOG.info("ReminderProcessing (horas antes) iniciado. candidatos={}", appointmentIds.size());
    int processed = 0;
    for (UUID appointmentId : appointmentIds) {
      processed += processHoursBeforeAppointment(appointmentId);
    }
    LOG.info("ReminderProcessing (horas antes) finalizado. processed={}", processed);
    return processed;
  }

  public int processAppointment(UUID appointmentId) {
    Integer resultado = requiresNewTransaction.execute(status -> processAppointmentInternal(appointmentId));
    return resultado == null ? 0 : resultado;
  }

  public int processHoursBeforeAppointment(UUID appointmentId) {
    Integer resultado =
        requiresNewTransaction.execute(status -> processHoursBeforeAppointmentInternal(appointmentId));
    return resultado == null ? 0 : resultado;
  }

  private int processAppointmentInternal(UUID appointmentId) {
    Agendamento appointment = agendamentoRepository.findById(appointmentId).orElse(null);
    if (appointment == null) return 0;
    if (jaEnviado(appointment.getTenantId(), appointment.getId(), CHANNEL_WHATSAPP_REMINDER, CHANNEL_TELEGRAM_REMINDER)) {
      return 0;
    }
    if (tenantRepository.findById(appointment.getTenantId()).isEmpty()) return 0;

    TenantOperationalSettings settings =
        tenantOperationalSettingsRepository.findByTenantIdOrCreate(appointment.getTenantId());
    if (!settings.isD1ReminderEnabled()) return 0;
    if (ZonedDateTime.now(ZONE_BR).toLocalTime().isBefore(parseHoraOuPadrao(settings.getD1ReminderHora()))) return 0;

    Cliente client = clienteRepository
        .findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId())
        .orElse(null);
    if (client == null) return 0;

    String message = buildReminderMessage(client.getName(), appointment.getDate(), appointment.getStartTime());
    return enviarLembrete(appointment, client, CHANNEL_WHATSAPP_REMINDER, CHANNEL_TELEGRAM_REMINDER, message);
  }

  private int processHoursBeforeAppointmentInternal(UUID appointmentId) {
    Agendamento appointment = agendamentoRepository.findById(appointmentId).orElse(null);
    if (appointment == null) return 0;
    if (jaEnviado(
        appointment.getTenantId(), appointment.getId(), CHANNEL_WHATSAPP_REMINDER_HOURS, CHANNEL_TELEGRAM_REMINDER_HOURS)) {
      return 0;
    }
    if (tenantRepository.findById(appointment.getTenantId()).isEmpty()) return 0;

    TenantOperationalSettings settings =
        tenantOperationalSettingsRepository.findByTenantIdOrCreate(appointment.getTenantId());
    if (!settings.isHoursBeforeReminderEnabled()) return 0;
    int horasAntes = Math.min(Math.max(settings.getReminderHours(), 1), MAX_HOURS_BEFORE);

    Instant appointmentInstant = parseAppointmentInstant(appointment);
    if (appointmentInstant == null) return 0;
    Instant now = Instant.now();
    Instant threshold = appointmentInstant.minus(horasAntes, ChronoUnit.HOURS);
    if (now.isBefore(threshold) || now.isAfter(appointmentInstant)) return 0;

    Cliente client = clienteRepository
        .findByIdAndTenantId(appointment.getClientId(), appointment.getTenantId())
        .orElse(null);
    if (client == null) return 0;

    String message = buildReminderMessage(client.getName(), appointment.getDate(), appointment.getStartTime());
    return enviarLembrete(appointment, client, CHANNEL_WHATSAPP_REMINDER_HOURS, CHANNEL_TELEGRAM_REMINDER_HOURS, message);
  }

  private int enviarLembrete(
      Agendamento appointment, Cliente client, String canalWhatsapp, String canalTelegram, String message) {
    ReminderRoute route = resolveReminderRoute(appointment.getTenantId(), client);
    if (!isChannelEnabledForReminders(appointment.getTenantId(), route.channel())) return 0;

    // Gate LGPD atual: opt-out somente para WhatsApp.
    if (route.channel() == ChatChannel.WHATSAPP && Boolean.TRUE.equals(client.getWhatsappOptOut())) {
      LOG.debug("Lembrete WhatsApp ignorado: cliente={} optou por nao receber mensagens", client.getId());
      return 0;
    }

    StatusNotification notificationStatus;
    String errorMessage = null;
    Instant sentAt = null;

    try {
      ChannelSendResult sendResult =
          sendReminderMessage(appointment.getTenantId(), route.channel(), route.destination(), message);
      if (!sendResult.success()) {
        throw new IllegalStateException(firstNonBlank(
            sendResult.providerErrorMessage(), sendResult.providerErrorCode(), "Falha ao enviar lembrete"));
      }
      notificationStatus = StatusNotification.SENT;
      sentAt = Instant.now();
      seedAssistantContext(appointment, client, route.destination());
    } catch (RuntimeException e) {
      notificationStatus = StatusNotification.FAILED;
      errorMessage = sanitizeErrorMessage(e.getMessage());
      LOG.warn(
          "Falha ao enviar lembrete canal={} para appointment={} tenant={}",
          route.channel(),
          appointment.getId(),
          appointment.getTenantId(),
          e);
    }

    notificationPublisher.publish(
        appointment.getTenantId(),
        appointment.getId(),
        route.channel() == ChatChannel.TELEGRAM ? canalTelegram : canalWhatsapp,
        route.destination(),
        message,
        notificationStatus,
        errorMessage,
        sentAt);
    return 1;
  }

  private Instant parseAppointmentInstant(Agendamento appointment) {
    try {
      LocalTime startTime = LocalTime.parse(appointment.getStartTime());
      return appointment.getDate().atTime(startTime).atZone(ZONE_BR).toInstant();
    } catch (Exception e) {
      return null;
    }
  }

  private LocalTime parseHoraOuPadrao(String hora) {
    try {
      return LocalTime.parse(hora);
    } catch (Exception e) {
      return LocalTime.of(18, 0);
    }
  }

  ChannelSendResult sendReminderMessage(UUID tenantId, ChatChannel channel, String destination, String message) {
    return communicationChannelDispatcher.sendText(new ChannelSendCommand(tenantId, channel, destination, message));
  }

  ReminderRoute resolveReminderRoute(UUID tenantId, Cliente client) {
    CustomerCommunicationChannelResolver.ResolvedChannel resolvedChannel =
        customerCommunicationChannelResolver.resolve(tenantId, client, client.getPhone());
    if (resolvedChannel.channel() == ChatChannel.TELEGRAM) {
      return new ReminderRoute(ChatChannel.TELEGRAM, resolvedChannel.externalContactId());
    }

    String whatsAppDestination = normalizeDigits(resolvedChannel.externalContactId());
    if (whatsAppDestination.length() < 10) {
      throw new IllegalArgumentException("Cliente sem telefone valido para lembrete via WhatsApp.");
    }
    return new ReminderRoute(ChatChannel.WHATSAPP, whatsAppDestination);
  }

  private void seedAssistantContext(Agendamento appointment, Cliente client, String userIdentifier) {
    try {
      assistantApiClient.seedReminderContext(
          appointment.getTenantId().toString(),
          userIdentifier,
          appointment.getId().toString(),
          client.getName() != null ? client.getName() : "");
      LOG.info("Contexto de lembrete semeado: appointment={}", appointment.getId());
    } catch (Exception e) {
      LOG.warn("Falha ao semear contexto no assistente (appointment={})", appointment.getId(), e);
    }
  }

  private boolean jaEnviado(UUID tenantId, UUID appointmentId, String canalWhatsapp, String canalTelegram) {
    return notificationRepository.existsSentByAppointmentAndChannels(
        tenantId, appointmentId, List.of(canalWhatsapp, canalTelegram));
  }

  private boolean isChannelEnabledForReminders(UUID tenantId, ChatChannel channel) {
    if (channel == ChatChannel.TELEGRAM) {
      TenantTelegramConfig config = tenantTelegramConfigRepository.findByTenantId(tenantId);
      return config != null && config.isTelegramEnabled();
    }
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findByTenantIdOrCreate(tenantId);
    return config != null && config.isWhatsappEnabled() && config.isProactiveAllowed();
  }

  private String buildReminderMessage(String clientName, LocalDate date, String startTime) {
    String normalizedName = (clientName == null || clientName.isBlank()) ? "cliente" : clientName;
    return "Ola, " + normalizedName + "! Lembrete do seu agendamento em "
        + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        + " as " + startTime + ". Responda CONFIRMAR ou CANCELAR.";
  }

  private String sanitizeErrorMessage(String message) {
    if (message == null || message.isBlank()) return "Erro nao especificado";
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private String normalizeDigits(String value) {
    if (value == null) return "";
    return value.replaceAll("\\D", "");
  }

  record ReminderRoute(ChatChannel channel, String destination) {}
}
