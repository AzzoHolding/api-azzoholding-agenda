package br.com.phdigitalcode.azzo.agenda.pro.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Espelha {@code modules/notifications/application/NotificationPersistenceObserver.java}. O
 * {@code @Observes} do CDI vira {@link EventListener} do Spring — o {@link NotificationPublisher}
 * publica de forma sincrona (equivalente a {@code Event.fire}) e este observer e quem defere o
 * trabalho de persistencia para depois do commit, via {@link AfterCommitExecutor}.
 */
@Component
public class NotificationPersistenceObserver {

  private final AfterCommitExecutor afterCommitExecutor;
  private final NotificationPersistenceWorker notificationPersistenceWorker;

  public NotificationPersistenceObserver(
      AfterCommitExecutor afterCommitExecutor,
      NotificationPersistenceWorker notificationPersistenceWorker) {
    this.afterCommitExecutor = afterCommitExecutor;
    this.notificationPersistenceWorker = notificationPersistenceWorker;
  }

  @EventListener
  void onNotificationRequested(NotificationRequestedEvent event) {
    if (event == null) return;
    afterCommitExecutor.run(() -> notificationPersistenceWorker.persist(event));
  }
}
