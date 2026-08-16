package br.com.phdigitalcode.azzo.agenda.pro.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Espelha {@code infrastructure/transaction/AfterCommitExecutor.java}. */
class AfterCommitExecutorTest {

  private final AfterCommitExecutor executor = new AfterCommitExecutor();

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    executor.shutdown();
  }

  @Test
  void runIgnoraTarefaNula() {
    executor.run(null);
    // nao lanca excecao — comportamento e apenas "nao fazer nada"
  }

  @Test
  void runExecutaImediatamenteEmBackgroundQuandoNaoHaTransacaoAtiva() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);

    executor.run(latch::countDown);

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void runAdiaExecucaoParaDepoisDoCommitQuandoHaTransacaoAtiva() throws InterruptedException {
    AtomicBoolean executadoAntesDoCommit = new AtomicBoolean(false);
    CountDownLatch latch = new CountDownLatch(1);
    TransactionSynchronizationManager.initSynchronization();

    executor.run(
        () -> {
          executadoAntesDoCommit.set(true);
          latch.countDown();
        });

    // Ainda nao rodou de forma sincrona: so dispara depois do commit.
    assertThat(latch.getCount()).isEqualTo(1);

    TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(executadoAntesDoCommit.get()).isTrue();
  }

  @Test
  void runNaoExecutaSeTransacaoNaoCommitou() {
    AtomicBoolean executado = new AtomicBoolean(false);
    TransactionSynchronizationManager.initSynchronization();

    executor.run(() -> executado.set(true));
    // Simula rollback: nenhuma chamada a afterCommit(), apenas limpeza da sincronizacao.
    TransactionSynchronizationManager.clearSynchronization();

    assertThat(executado.get()).isFalse();
  }

  @Test
  void runToleraExcecaoLancadaPelaTarefa() throws InterruptedException {
    AtomicInteger execucoes = new AtomicInteger(0);
    CountDownLatch latch = new CountDownLatch(1);

    executor.run(
        () -> {
          execucoes.incrementAndGet();
          latch.countDown();
          throw new RuntimeException("falha proposital");
        });

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(execucoes.get()).isEqualTo(1);
  }
}
