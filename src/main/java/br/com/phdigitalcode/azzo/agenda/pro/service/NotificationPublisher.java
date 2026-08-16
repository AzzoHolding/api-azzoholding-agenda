package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * Espelha {@code modules/notifications/application/NotificationPublisher.java}. O
 * {@code jakarta.enterprise.event.Event<NotificationRequestedEvent>} (CDI) vira
 * {@link ApplicationEventPublisher} do Spring — {@link #publish(NotificationRequestedEvent)}
 * dispara o evento de forma sincrona para os listeners registrados (mesmo comportamento de
 * {@code Event.fire}), e quem decide adiar o trabalho para depois do commit e o observer
 * ({@link NotificationPersistenceObserver}), nao o publisher.
 */
@Component
public class NotificationPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationPublisher.class);

  private final ApplicationEventPublisher applicationEventPublisher;

  public NotificationPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public void publish(NotificationRequestedEvent event) {
    if (event == null) return;
    LOG.info(
        "notificationPublisher.publish {}",
        CorrelatedLogging.context(
            "tenantId", event.tenantId(),
            "appointmentId", event.appointmentId(),
            "professionalId", event.professionalId(),
            "channel", event.channel(),
            "destination", event.destination(),
            "status", event.status()));
    applicationEventPublisher.publishEvent(event);
  }

  public void publish(
      UUID tenantId,
      UUID appointmentId,
      String channel,
      String destination,
      String message,
      StatusNotification status,
      String errorMessage,
      Instant sentAt) {
    publish(tenantId, appointmentId, null, channel, destination, message, status, errorMessage, sentAt, null);
  }

  public void publish(
      UUID tenantId,
      UUID appointmentId,
      String channel,
      String destination,
      String message,
      StatusNotification status,
      String errorMessage,
      Instant sentAt,
      Long deduplicationWindowSeconds) {
    publish(tenantId, appointmentId, null, channel, destination, message, status, errorMessage, sentAt, deduplicationWindowSeconds);
  }

  public void publish(
      UUID tenantId,
      UUID appointmentId,
      UUID professionalId,
      String channel,
      String destination,
      String message,
      StatusNotification status,
      String errorMessage,
      Instant sentAt) {
    publish(tenantId, appointmentId, professionalId, channel, destination, message, status, errorMessage, sentAt, null);
  }

  public void publish(
      UUID tenantId,
      UUID appointmentId,
      UUID professionalId,
      String channel,
      String destination,
      String message,
      StatusNotification status,
      String errorMessage,
      Instant sentAt,
      Long deduplicationWindowSeconds) {
    publish(new NotificationRequestedEvent(
        tenantId,
        appointmentId,
        professionalId,
        channel,
        destination,
        message,
        status,
        errorMessage,
        sentAt,
        deduplicationWindowSeconds));
  }
}
