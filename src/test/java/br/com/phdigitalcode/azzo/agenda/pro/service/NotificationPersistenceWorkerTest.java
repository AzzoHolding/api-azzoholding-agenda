package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Notification;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;
import br.com.phdigitalcode.azzo.agenda.pro.repository.NotificationRepository;

/** Espelha {@code modules/notifications/application/NotificationPersistenceWorker.java}. */
class NotificationPersistenceWorkerTest {

  private NotificationRepository notificationRepository;
  private NotificationPersistenceWorker worker;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(NotificationRepository.class);
    worker = new NotificationPersistenceWorker(notificationRepository);
  }

  @Test
  void persistIgnoraEventoNulo() {
    worker.persist(null);
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistIgnoraEventoSemTenantId() {
    worker.persist(evento(null, "canal", "destino", StatusNotification.SENT, null, null));
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistIgnoraEventoComChannelEmBranco() {
    worker.persist(evento(UUID.randomUUID(), " ", "destino", StatusNotification.SENT, null, null));
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistIgnoraEventoComDestinationEmBranco() {
    worker.persist(evento(UUID.randomUUID(), "canal", "", StatusNotification.SENT, null, null));
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistIgnoraEventoComStatusNulo() {
    worker.persist(evento(UUID.randomUUID(), "canal", "destino", null, null, null));
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistSalvaQuandoSemJanelaDeDeduplicacao() {
    UUID tenantId = UUID.randomUUID();
    worker.persist(evento(tenantId, "canal", "destino", StatusNotification.SENT, null, null));

    ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
    verify(notificationRepository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getChannel()).isEqualTo("canal");
    assertThat(captor.getValue().getDestination()).isEqualTo("destino");
    assertThat(captor.getValue().getStatus()).isEqualTo(StatusNotification.SENT);
  }

  @Test
  void persistIgnoraQuandoDuplicataPorMensagemDentroDaJanela() {
    UUID tenantId = UUID.randomUUID();
    when(notificationRepository.findRecentByMessage(
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("canal"),
            org.mockito.ArgumentMatchers.eq("destino"),
            org.mockito.ArgumentMatchers.eq(StatusNotification.SENT),
            org.mockito.ArgumentMatchers.eq("msg"),
            any()))
        .thenReturn(List.of(new Notification()));

    worker.persist(evento(tenantId, "canal", "destino", StatusNotification.SENT, "msg", 60L));

    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistIgnoraQuandoDuplicataPorErrorMessageDentroDaJanela() {
    UUID tenantId = UUID.randomUUID();
    when(notificationRepository.findRecentByErrorMessage(
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("canal"),
            org.mockito.ArgumentMatchers.eq("destino"),
            org.mockito.ArgumentMatchers.eq(StatusNotification.FAILED),
            org.mockito.ArgumentMatchers.eq("falhou"),
            any()))
        .thenReturn(List.of(new Notification()));

    NotificationRequestedEvent event =
        new NotificationRequestedEvent(
            tenantId, null, null, "canal", "destino", "msg", StatusNotification.FAILED, "falhou", null, 60L);
    worker.persist(event);

    verify(notificationRepository, never()).save(any());
  }

  @Test
  void persistSalvaQuandoJanelaDeDeduplicacaoNaoEncontraDuplicata() {
    UUID tenantId = UUID.randomUUID();
    when(notificationRepository.findRecentByMessage(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    worker.persist(evento(tenantId, "canal", "destino", StatusNotification.SENT, "msg", 60L));

    verify(notificationRepository).save(any());
  }

  @Test
  void persistSalvaQuandoJanelaDeDeduplicacaoZeroOuNegativaDesativaVerificacao() {
    UUID tenantId = UUID.randomUUID();
    worker.persist(evento(tenantId, "canal", "destino", StatusNotification.SENT, "msg", 0L));
    worker.persist(evento(tenantId, "canal", "destino", StatusNotification.SENT, "msg", -5L));

    verify(notificationRepository, never()).findRecentByMessage(any(), any(), any(), any(), any(), any());
    verify(notificationRepository, org.mockito.Mockito.times(2)).save(any());
  }

  private NotificationRequestedEvent evento(
      UUID tenantId,
      String channel,
      String destination,
      StatusNotification status,
      String message,
      Long dedupWindowSeconds) {
    return new NotificationRequestedEvent(
        tenantId, null, null, channel, destination, message, status, null, Instant.now(), dedupWindowSeconds);
  }
}
