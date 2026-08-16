package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusNotification;

/** Espelha {@code modules/notifications/application/NotificationPersistenceObserver.java}. */
class NotificationPersistenceObserverTest {

  private AfterCommitExecutor afterCommitExecutor;
  private NotificationPersistenceWorker notificationPersistenceWorker;
  private NotificationPersistenceObserver observer;

  @BeforeEach
  void setUp() {
    afterCommitExecutor = mock(AfterCommitExecutor.class);
    notificationPersistenceWorker = mock(NotificationPersistenceWorker.class);
    observer = new NotificationPersistenceObserver(afterCommitExecutor, notificationPersistenceWorker);
  }

  @Test
  void onNotificationRequestedIgnoraEventoNulo() {
    observer.onNotificationRequested(null);
    verify(afterCommitExecutor, never()).run(any());
  }

  @Test
  void onNotificationRequestedDeferePersistenciaViaAfterCommitExecutor() {
    NotificationRequestedEvent event =
        new NotificationRequestedEvent(
            UUID.randomUUID(),
            null,
            null,
            "canal",
            "destino",
            "msg",
            StatusNotification.SENT,
            null,
            Instant.now(),
            null);

    observer.onNotificationRequested(event);

    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(afterCommitExecutor).run(captor.capture());
    verify(notificationPersistenceWorker, never()).persist(any());

    captor.getValue().run();
    verify(notificationPersistenceWorker).persist(event);
  }
}
