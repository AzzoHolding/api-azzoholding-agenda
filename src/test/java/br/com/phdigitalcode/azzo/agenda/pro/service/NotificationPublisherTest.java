package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;

/** Espelha {@code modules/notifications/application/NotificationPublisher.java}. */
class NotificationPublisherTest {

  private ApplicationEventPublisher applicationEventPublisher;
  private NotificationPublisher publisher;

  @BeforeEach
  void setUp() {
    applicationEventPublisher = mock(ApplicationEventPublisher.class);
    publisher = new NotificationPublisher(applicationEventPublisher);
  }

  @Test
  void publishComEventoNuloNaoDisparaNada() {
    publisher.publish((NotificationRequestedEvent) null);
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void publishComEventoDisparaViaApplicationEventPublisher() {
    NotificationRequestedEvent event =
        new NotificationRequestedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "whatsapp",
            "1199999999",
            "msg",
            StatusNotification.SENT,
            null,
            Instant.now(),
            null);

    publisher.publish(event);

    verify(applicationEventPublisher).publishEvent(event);
  }

  @Test
  void publishComTenantAppointmentChannelDestinationMessageStatusErrorSentAtMontaEvento() {
    UUID tenantId = UUID.randomUUID();
    UUID appointmentId = UUID.randomUUID();
    Instant sentAt = Instant.now();

    publisher.publish(
        tenantId, appointmentId, "canal", "destino", "msg", StatusNotification.FAILED, "erro", sentAt);

    ArgumentCaptor<NotificationRequestedEvent> captor =
        ArgumentCaptor.forClass(NotificationRequestedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    NotificationRequestedEvent event = captor.getValue();
    assertThat(event.tenantId()).isEqualTo(tenantId);
    assertThat(event.appointmentId()).isEqualTo(appointmentId);
    assertThat(event.professionalId()).isNull();
    assertThat(event.channel()).isEqualTo("canal");
    assertThat(event.destination()).isEqualTo("destino");
    assertThat(event.message()).isEqualTo("msg");
    assertThat(event.status()).isEqualTo(StatusNotification.FAILED);
    assertThat(event.errorMessage()).isEqualTo("erro");
    assertThat(event.sentAt()).isEqualTo(sentAt);
    assertThat(event.deduplicationWindowSeconds()).isNull();
  }

  @Test
  void publishComJanelaDeDeduplicacaoPropagaValor() {
    publisher.publish(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "canal",
        "destino",
        "msg",
        StatusNotification.SENT,
        null,
        Instant.now(),
        60L);

    ArgumentCaptor<NotificationRequestedEvent> captor =
        ArgumentCaptor.forClass(NotificationRequestedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().deduplicationWindowSeconds()).isEqualTo(60L);
  }

  @Test
  void publishComProfessionalIdPropagaValor() {
    UUID professionalId = UUID.randomUUID();

    publisher.publish(
        UUID.randomUUID(),
        UUID.randomUUID(),
        professionalId,
        "canal",
        "destino",
        "msg",
        StatusNotification.SENT,
        null,
        Instant.now());

    ArgumentCaptor<NotificationRequestedEvent> captor =
        ArgumentCaptor.forClass(NotificationRequestedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().professionalId()).isEqualTo(professionalId);
  }

  @Test
  void publishComProfessionalIdEJanelaDeDeduplicacaoPropagaAmbos() {
    UUID professionalId = UUID.randomUUID();

    publisher.publish(
        UUID.randomUUID(),
        UUID.randomUUID(),
        professionalId,
        "canal",
        "destino",
        "msg",
        StatusNotification.SENT,
        null,
        Instant.now(),
        90L);

    ArgumentCaptor<NotificationRequestedEvent> captor =
        ArgumentCaptor.forClass(NotificationRequestedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().professionalId()).isEqualTo(professionalId);
    assertThat(captor.getValue().deduplicationWindowSeconds()).isEqualTo(90L);
  }
}
