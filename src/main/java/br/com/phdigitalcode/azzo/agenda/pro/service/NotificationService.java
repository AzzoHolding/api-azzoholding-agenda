package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.dto.notification.NotificationDtos.NotificationListResponse;
import br.com.phdigitalcode.azzo.agenda.pro.dto.notification.NotificationDtos.NotificationResponse;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Notification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Profissional;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Servico;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.repository.AgendamentoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ClienteRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ProfissionalRepository;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ServicoRepository;
import br.com.phdigitalcode.azzo.agenda.pro.security.AuthenticatedUser;
import br.com.phdigitalcode.azzo.agenda.pro.security.ContextoTenant;
import br.com.phdigitalcode.azzo.agenda.pro.specification.NotificationSpecifications;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/** Espelha {@code modules/notifications/application/ServicoNotificacoes.java}. */
@Service
public class NotificationService {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notificationRepository;
  private final NotificationPublisher notificationPublisher;
  private final AgendamentoRepository agendamentoRepository;
  private final ClienteRepository clienteRepository;
  private final ProfissionalRepository profissionalRepository;
  private final ServicoRepository servicoRepository;
  private final ContextoTenant contextoTenant;
  private final AuthenticatedUser authenticatedUser;

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationPublisher notificationPublisher,
      AgendamentoRepository agendamentoRepository,
      ClienteRepository clienteRepository,
      ProfissionalRepository profissionalRepository,
      ServicoRepository servicoRepository,
      ContextoTenant contextoTenant,
      AuthenticatedUser authenticatedUser) {
    this.notificationRepository = notificationRepository;
    this.notificationPublisher = notificationPublisher;
    this.agendamentoRepository = agendamentoRepository;
    this.clienteRepository = clienteRepository;
    this.profissionalRepository = profissionalRepository;
    this.servicoRepository = servicoRepository;
    this.contextoTenant = contextoTenant;
    this.authenticatedUser = authenticatedUser;
  }

  @Transactional
  public void registrarCriacaoAgendamento(
      UUID tenantId, UUID appointmentId, UUID clientId, String channel) {
    if (tenantId == null || appointmentId == null) return;

    String normalizedChannel = (channel == null || channel.isBlank()) ? "APPOINTMENT_CREATED" : channel.trim();
    String destination = resolverDestinoCliente(tenantId, clientId);

    boolean existing =
        notificationRepository
            .findFirstByTenantIdAndAppointmentIdAndChannelAndDestination(
                tenantId, appointmentId, normalizedChannel, destination)
            .isPresent();
    if (existing) {
      LOG.warn(
          "notifications.registerAppointmentCreation.duplicate {}",
          CorrelatedLogging.context(
              "tenantId", tenantId,
              "appointmentId", appointmentId,
              "clientId", clientId,
              "channel", normalizedChannel,
              "destination", destination));
      return;
    }

    UUID appointmentProfessionalId = resolverProfissionalDoAgendamento(tenantId, appointmentId);

    notificationPublisher.publish(
        tenantId,
        appointmentId,
        appointmentProfessionalId,
        normalizedChannel,
        destination,
        montarMensagemCriacaoAgendamento(tenantId, appointmentId),
        StatusNotification.SENT,
        null,
        Instant.now());
    LOG.info(
        "notifications.registerAppointmentCreation.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "appointmentId", appointmentId,
            "clientId", clientId,
            "professionalId", appointmentProfessionalId,
            "channel", normalizedChannel,
            "destination", destination));
  }

  @Transactional(readOnly = true)
  public NotificationListResponse listar(
      String status,
      String channel,
      boolean failedOnly,
      boolean unreadOnly,
      int limit,
      Instant cursorCreatedAt,
      UUID cursorId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    LOG.info(
        "notifications.list.started {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "status", status,
            "channel", channel,
            "failedOnly", failedOnly,
            "unreadOnly", unreadOnly,
            "limit", limit));

    UUID professionalScopeId = null;
    if (isProfessionalRole()) {
      professionalScopeId = resolverProfissionalDoUsuarioLogado(tenantId);
    }

    Specification<Notification> spec =
        NotificationSpecifications.listar(
            tenantId,
            professionalScopeId,
            channel,
            failedOnly,
            status,
            unreadOnly,
            cursorCreatedAt,
            cursorId);

    NotificationListResponse response = buscarPagina(spec, limit);

    LOG.info(
        "notifications.list.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "count", response.items.size(),
            "hasMore", response.hasMore));

    return response;
  }

  @Transactional(readOnly = true)
  public NotificationListResponse listarMeusAgendamentos(
      boolean unreadOnly, int limit, Instant cursorCreatedAt, UUID cursorId) {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    UUID professionalId = resolverProfissionalDoUsuarioLogado(tenantId);

    Specification<Notification> spec =
        NotificationSpecifications.meusAgendamentos(
            tenantId, professionalId, unreadOnly, cursorCreatedAt, cursorId);

    NotificationListResponse response = buscarPagina(spec, limit);

    LOG.info(
        "notifications.listMyAppointments.completed {}",
        CorrelatedLogging.context(
            "tenantId", tenantId,
            "professionalId", professionalId,
            "count", response.items.size(),
            "hasMore", response.hasMore));

    return response;
  }

  @Transactional
  public boolean remover(UUID notificationId) {
    if (notificationId == null) return false;
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    boolean removed = notificationRepository.deleteByIdAndTenantId(notificationId, tenantId) > 0;
    if (removed) {
      LOG.info(
          "notifications.remove.completed {}",
          CorrelatedLogging.context("tenantId", tenantId, "notificationId", notificationId));
    } else {
      LOG.warn(
          "notifications.remove.notFound {}",
          CorrelatedLogging.context("tenantId", tenantId, "notificationId", notificationId));
    }
    return removed;
  }

  @Transactional
  public long removerTodasDoTenant() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    long removed = notificationRepository.deleteByTenantId(tenantId);
    LOG.info(
        "notifications.removeAll.completed {}",
        CorrelatedLogging.context("tenantId", tenantId, "deleted", removed));
    return removed;
  }

  @Transactional
  public boolean marcarComoVisualizada(UUID notificationId) {
    if (notificationId == null) return false;
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    Notification notification =
        notificationRepository.findByIdAndTenantId(notificationId, tenantId).orElse(null);
    if (notification == null) {
      LOG.warn(
          "notifications.markViewed.notFound {}",
          CorrelatedLogging.context("tenantId", tenantId, "notificationId", notificationId));
      return false;
    }
    if (notification.getViewedAt() != null) {
      LOG.info(
          "notifications.markViewed.alreadyViewed {}",
          CorrelatedLogging.context("tenantId", tenantId, "notificationId", notificationId));
      return true;
    }
    notification.setViewedAt(Instant.now());
    LOG.info(
        "notifications.markViewed.completed {}",
        CorrelatedLogging.context("tenantId", tenantId, "notificationId", notificationId));
    return true;
  }

  @Transactional
  public long marcarTodasComoVisualizadas() {
    UUID tenantId = contextoTenant.obterTenantIdOuFalhar();
    long updated = notificationRepository.markAllViewedByTenant(tenantId, Instant.now());
    LOG.info(
        "notifications.markAllViewed.completed {}",
        CorrelatedLogging.context("tenantId", tenantId, "updated", updated));
    return updated;
  }

  private NotificationListResponse buscarPagina(Specification<Notification> spec, int limit) {
    int normalizedLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
    Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    List<Notification> fetched =
        notificationRepository
            .findAll(spec, PageRequest.of(0, normalizedLimit + 1, sort))
            .getContent();

    boolean hasMore = fetched.size() > normalizedLimit;
    List<Notification> pageItems = fetched.stream().limit(normalizedLimit).toList();

    NotificationListResponse response = new NotificationListResponse();
    response.items = pageItems.stream().map(this::toResponse).toList();
    response.hasMore = hasMore;

    if (hasMore && !pageItems.isEmpty()) {
      Notification last = pageItems.get(pageItems.size() - 1);
      response.nextCursorCreatedAt = last.getCreatedAt() != null ? last.getCreatedAt().toString() : null;
      response.nextCursorId = last.getId() != null ? last.getId().toString() : null;
    }

    return response;
  }

  private NotificationResponse toResponse(Notification notification) {
    NotificationResponse response = new NotificationResponse();
    response.id = notification.getId() != null ? notification.getId().toString() : null;
    response.tenantId = notification.getTenantId() != null ? notification.getTenantId().toString() : null;
    response.appointmentId =
        notification.getAppointmentId() != null ? notification.getAppointmentId().toString() : null;
    response.professionalId =
        notification.getProfessionalId() != null ? notification.getProfessionalId().toString() : null;
    response.channel = notification.getChannel();
    response.destination = notification.getDestination();
    response.message = notification.getMessage();
    response.status = notification.getStatus() != null ? notification.getStatus().name() : null;
    response.errorMessage = notification.getErrorMessage();
    response.sentAt = notification.getSentAt() != null ? notification.getSentAt().toString() : null;
    response.viewedAt = notification.getViewedAt() != null ? notification.getViewedAt().toString() : null;
    response.viewed = notification.getViewedAt() != null;
    response.createdAt = notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null;
    return response;
  }

  private boolean isProfessionalRole() {
    return authenticatedUser.temRole("PROFESSIONAL")
        && !authenticatedUser.temRole("OWNER")
        && !authenticatedUser.temRole("ADMIN");
  }

  private UUID resolverProfissionalDoUsuarioLogado(UUID tenantId) {
    UUID userId = authenticatedUser.idOuNulo();
    if (userId == null) return null;
    return profissionalRepository
        .findByTenantIdAndUserId(tenantId, userId)
        .map(Profissional::getId)
        .orElse(null);
  }

  private UUID resolverProfissionalDoAgendamento(UUID tenantId, UUID appointmentId) {
    if (appointmentId == null) return null;
    return agendamentoRepository
        .findByIdAndTenantId(appointmentId, tenantId)
        .map(Agendamento::getProfessionalId)
        .orElse(null);
  }

  private String resolverDestinoCliente(UUID tenantId, UUID clientId) {
    if (clientId == null) return "sistema";
    Cliente client = clienteRepository.findByIdAndTenantId(clientId, tenantId).orElse(null);
    if (client == null) return "sistema";

    String phone = normalizarDigitos(client.getPhone());
    if (!phone.isBlank()) return phone;
    if (client.getEmail() != null && !client.getEmail().isBlank()) return client.getEmail().trim().toLowerCase();
    return "sistema";
  }

  private String normalizarDigitos(String value) {
    if (value == null) return "";
    return value.replaceAll("\\D", "");
  }

  private String montarMensagemCriacaoAgendamento(UUID tenantId, UUID appointmentId) {
    Agendamento appointment =
        agendamentoRepository.findByIdAndTenantId(appointmentId, tenantId).orElse(null);
    if (appointment == null) return "Novo agendamento criado.";

    Profissional profissional =
        profissionalRepository
            .findByIdAndTenantId(appointment.getProfessionalId(), tenantId)
            .orElse(null);

    String serviceName =
        appointment.getItems().stream()
            .map(
                item ->
                    item.getService() != null
                        ? item.getService()
                        : servicoRepository.findByIdAndTenantId(item.getServiceId(), tenantId).orElse(null))
            .filter(Objects::nonNull)
            .map(Servico::getName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("Servico");
    String professionalName =
        profissional != null && profissional.getName() != null && !profissional.getName().isBlank()
            ? profissional.getName().trim()
            : "Profissional";
    String date = appointment.getDate() != null ? appointment.getDate().toString() : "-";
    String time =
        appointment.getStartTime() != null && !appointment.getStartTime().isBlank()
            ? appointment.getStartTime()
            : "-";

    return "Agendamento criado. Servico: "
        + serviceName
        + ", profissional: "
        + professionalName
        + ", data: "
        + date
        + ", horario: "
        + time
        + ".";
  }
}
