package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Espelha {@code modules/email/infrastructure/async/EmailJobExecutor.java}.
 *
 * <p>Mantido o fallback via reflection para {@code Executors.newVirtualThreadPerTaskExecutor()}
 * (em vez de chamar o metodo diretamente), tal como o original — nao "consertar" trocando por
 * chamada direta, mesmo o projeto rodando em Java 25 (que garante o metodo disponivel): e o
 * mesmo comportamento defensivo do Quarkus original, preservado por paridade.
 */
@Component
public class EmailJobExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(EmailJobExecutor.class);
  private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

  private final String executorMode;
  private final int poolSize;
  private final long processingTimeoutSeconds;

  private ExecutorService executor;

  public EmailJobExecutor(
      @Value("${app.email.jobs.executor.mode:virtual}") String executorMode,
      @Value("${app.email.jobs.executor.pool-size:4}") int poolSize,
      @Value("${app.email.jobs.processing-timeout-seconds:45}") long processingTimeoutSeconds) {
    this.executorMode = executorMode;
    this.poolSize = poolSize;
    this.processingTimeoutSeconds = processingTimeoutSeconds;
    this.executor = buildExecutor();
  }

  public <T> void runBatchAsync(Collection<T> items, Consumer<T> consumer, Runnable onComplete) {
    if (items == null || items.isEmpty() || consumer == null) return;

    executor.execute(() -> {
      List<Future<?>> futures = new ArrayList<>();
      try {
        for (T item : items) {
          futures.add(executor.submit(() -> consumer.accept(item)));
        }

        for (Future<?> future : futures) {
          try {
            future.get(Math.max(processingTimeoutSeconds, 1), TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execucao da fila de email interrompida.", e);
          } catch (TimeoutException e) {
            future.cancel(true);
            LOG.error(
                "Job da fila de email excedeu o timeout de {}s e foi interrompido.",
                Math.max(processingTimeoutSeconds, 1));
          } catch (Exception e) {
            LOG.error("Falha ao executar job da fila de email.", e);
          }
        }
      } finally {
        if (onComplete != null) onComplete.run();
      }
    });
  }

  private ExecutorService buildExecutor() {
    if ("virtual".equalsIgnoreCase(executorMode)) {
      ExecutorService virtualExecutor = tryCreateVirtualThreadExecutor();
      if (virtualExecutor != null) {
        LOG.info("Email job executor inicializado com virtual threads.");
        return virtualExecutor;
      }
      LOG.info("Virtual threads indisponiveis nesta runtime. Aplicando fallback para fixed thread pool.");
    }

    int size = Math.max(poolSize, 1);
    return Executors.newFixedThreadPool(size, buildThreadFactory());
  }

  private ExecutorService tryCreateVirtualThreadExecutor() {
    try {
      Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
      Object instance = method.invoke(null);
      if (instance instanceof ExecutorService executorService) {
        return executorService;
      }
    } catch (Exception ignored) {
      // Runtime sem suporte nativo a virtual threads.
    }
    return null;
  }

  private ThreadFactory buildThreadFactory() {
    return runnable -> {
      Thread thread = new Thread(runnable);
      thread.setName("email-job-" + THREAD_COUNTER.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }

  @PreDestroy
  void shutdown() {
    if (executor == null) return;
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
}
