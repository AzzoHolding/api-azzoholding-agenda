package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Notification;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/** Espelha {@code modules/notifications/application/NotificationPersistenceWorker.java}. */
@Service
public class NotificationPersistenceWorker {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationPersistenceWorker.class);

  private final NotificationRepository notificationRepository;

  public NotificationPersistenceWorker(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  @Transactional
  public void persist(NotificationRequestedEvent event) {
    if (event == null || event.tenantId() == null) return;
    if (isBlank(event.channel()) || isBlank(event.destination()) || event.status() == null) {
      LOG.warn(
          "notificationPersistence.invalidEvent {}",
          CorrelatedLogging.context(
              "tenantId", event.tenantId(),
              "channel", event.channel(),
              "destination", event.destination(),
              "status", event.status()));
      return;
    }
    if (isDuplicate(event)) {
      LOG.warn(
          "notificationPersistence.duplicate {}",
          CorrelatedLogging.context(
              "tenantId", event.tenantId(),
              "channel", event.channel(),
              "destination", event.destination(),
              "status", event.status()));
      return;
    }

    Notification notification = new Notification();
    notification.setTenantId(event.tenantId());
    notification.setAppointmentId(event.appointmentId());
    notification.setProfessionalId(event.professionalId());
    notification.setChannel(event.channel().trim());
    notification.setDestination(event.destination().trim());
    notification.setMessage(event.message());
    notification.setStatus(event.status());
    notification.setErrorMessage(event.errorMessage());
    notification.setSentAt(event.sentAt());
    notificationRepository.save(notification);
    LOG.info(
        "notificationPersistence.completed {}",
        CorrelatedLogging.context(
            "tenantId", event.tenantId(),
            "notificationId", notification.getId(),
            "appointmentId", event.appointmentId(),
            "professionalId", event.professionalId(),
            "channel", event.channel(),
            "destination", event.destination(),
            "status", event.status()));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private boolean isDuplicate(NotificationRequestedEvent event) {
    Long deduplicationWindowSeconds = event.deduplicationWindowSeconds();
    if (deduplicationWindowSeconds == null || deduplicationWindowSeconds <= 0) return false;

    Instant threshold = Instant.now().minusSeconds(deduplicationWindowSeconds);
    List<Notification> matches;
    if (!isBlank(event.errorMessage())) {
      matches =
          notificationRepository.findRecentByErrorMessage(
              event.tenantId(),
              event.channel().trim(),
              event.destination().trim(),
              event.status(),
              event.errorMessage(),
              threshold);
    } else {
      matches =
          notificationRepository.findRecentByMessage(
              event.tenantId(),
              event.channel().trim(),
              event.destination().trim(),
              event.status(),
              event.message(),
              threshold);
    }
    return !matches.isEmpty();
  }
}
