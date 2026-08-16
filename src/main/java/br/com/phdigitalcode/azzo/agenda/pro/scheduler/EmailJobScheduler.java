package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;

/**
 * Espelha {@code modules/email/infrastructure/scheduler/EmailJobScheduler.java}.
 *
 * <p>{@code fixedRateString} reproduz o {@code @Scheduled(every = "3m")} do original (periodo
 * fixo entre inicios de execucao, nao entre o fim de uma e o inicio da proxima). O {@link
 * AtomicBoolean} de guarda e preservado tal como no original: diferente de
 * {@code NfsePdfJobScheduler}/{@code FiscalDanfeJobScheduler} (que processam tudo de forma
 * sincrona dentro do metodo agendado), aqui {@code processPendingJobsAsync} despacha o trabalho
 * para um executor em background e retorna quase imediatamente — o {@code ConcurrentExecution.SKIP}
 * do Quarkus (e o equivalente implicito do {@code @Scheduled} do Spring, que so reagenda apos o
 * metodo retornar) protegeria apenas a chamada do metodo em si, nao o processamento assincrono que
 * ele disparou. Por isso o guard manual continua sendo a unica protecao real contra rodadas
 * sobrepostas.
 */
@Component
public class EmailJobScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(EmailJobScheduler.class);

  private final EmailJobService emailJobService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public EmailJobScheduler(EmailJobService emailJobService) {
    this.emailJobService = emailJobService;
  }

  @Scheduled(fixedRateString = "${app.email.jobs.scheduler.every:PT3M}")
  void processarFila() {
    if (!running.compareAndSet(false, true)) {
      LOG.info("Email job scheduler ignorado porque ainda existe rodada em execucao.");
      return;
    }

    try {
      int processed = emailJobService.processPendingJobsAsync(() -> running.set(false));
      if (processed > 0) {
        LOG.info("email_job_scheduler processed={}", processed);
      } else {
        running.set(false);
      }
    } catch (RuntimeException e) {
      running.set(false);
      throw e;
    }
  }
}
