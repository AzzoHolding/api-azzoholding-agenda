package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/** Cobre {@code EmailJobExecutor} (espelha {@code modules/email/infrastructure/async/EmailJobExecutor.java}). */
class EmailJobExecutorTest {

  @Test
  void runBatchAsyncNaoFazNadaQuandoListaVazia() throws Exception {
    EmailJobExecutor executor = new EmailJobExecutor("virtual", 2, 5);
    AtomicBoolean completed = new AtomicBoolean(false);

    executor.runBatchAsync(List.of(), item -> {}, () -> completed.set(true));

    // Sem itens, o batch nem entra na fila do executor — onComplete nunca dispara.
    Thread.sleep(50);
    assertThat(completed.get()).isFalse();
  }

  @Test
  void runBatchAsyncNaoFazNadaQuandoConsumerNulo() throws Exception {
    EmailJobExecutor executor = new EmailJobExecutor("virtual", 2, 5);
    AtomicBoolean completed = new AtomicBoolean(false);

    executor.runBatchAsync(List.of("a", "b"), null, () -> completed.set(true));

    Thread.sleep(50);
    assertThat(completed.get()).isFalse();
  }

  @Test
  void runBatchAsyncProcessaTodosOsItensEChamaOnCompleteComThreadPoolFixo() throws Exception {
    EmailJobExecutor executor = new EmailJobExecutor("fixed", 2, 5);
    List<String> processed = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    executor.runBatchAsync(List.of("a", "b", "c"), processed::add, latch::countDown);

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(processed).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void runBatchAsyncProcessaTodosOsItensComVirtualThreads() throws Exception {
    EmailJobExecutor executor = new EmailJobExecutor("virtual", 4, 5);
    List<String> processed = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    executor.runBatchAsync(List.of("x", "y"), processed::add, latch::countDown);

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(processed).containsExactlyInAnyOrder("x", "y");
  }

  @Test
  void runBatchAsyncChamaOnCompleteMesmoQuandoUmItemLancaExcecao() throws Exception {
    EmailJobExecutor executor = new EmailJobExecutor("virtual", 2, 5);
    List<String> processed = new CopyOnWriteArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);

    executor.runBatchAsync(
        List.of("ok", "boom"),
        item -> {
          if ("boom".equals(item)) throw new RuntimeException("falha simulada");
          processed.add(item);
        },
        latch::countDown);

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(processed).containsExactly("ok");
  }
}
