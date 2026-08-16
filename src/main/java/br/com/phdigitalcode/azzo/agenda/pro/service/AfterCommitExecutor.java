package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;

/**
 * Espelha {@code infrastructure/transaction/AfterCommitExecutor.java}: agenda uma tarefa para
 * rodar depois do commit da transacao atual, em uma thread de fundo dedicada; se nao houver
 * transacao ativa, roda de imediato (tambem em background).
 *
 * <p>O original usa {@code TransactionSynchronizationRegistry}/{@code Synchronization} (JTA) do
 * Quarkus; aqui o equivalente e {@link TransactionSynchronizationManager}/
 * {@link TransactionSynchronization#afterCommit()} do Spring, que so dispara quando a transacao
 * realmente commitou (nunca em rollback) — mesma semantica de {@code STATUS_COMMITTED} do
 * original.
 */
@Component
public class AfterCommitExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(AfterCommitExecutor.class);
  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

  private final ExecutorService executor =
      Executors.newFixedThreadPool(2, new AfterCommitThreadFactory());

  public void run(Runnable task) {
    if (task == null) return;

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              runAsync(task);
            }
          });
      return;
    }

    runAsync(task);
  }

  private void runAsync(Runnable task) {
    executor.execute(
        () -> {
          try {
            task.run();
          } catch (Exception e) {
            LOG.error("Falha na execucao assincrona pos-commit.", e);
          }
        });
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }

  private static final class AfterCommitThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setName("azzo-after-commit-" + THREAD_COUNTER.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    }
  }
}
