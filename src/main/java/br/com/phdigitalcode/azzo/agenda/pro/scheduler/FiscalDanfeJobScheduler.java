package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.FiscalDanfeJobService;

/**
 * Espelha {@code modules/fiscal/infrastructure/scheduler/FiscalDanfeJobScheduler.java}
 * ({@code @Scheduled(every = "20s", delayed = "35s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina.
 */
@Component
public class FiscalDanfeJobScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(FiscalDanfeJobScheduler.class);

  private final FiscalDanfeJobService fiscalDanfeJobService;

  public FiscalDanfeJobScheduler(FiscalDanfeJobService fiscalDanfeJobService) {
    this.fiscalDanfeJobService = fiscalDanfeJobService;
  }

  @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT35S")
  void processarFilaDanfe() {
    try {
      int processados = fiscalDanfeJobService.processarPendentes();
      if (processados > 0) {
        LOG.info("Fiscal DANFE scheduler processou {} job(s).", processados);
      }
    } catch (RuntimeException e) {
      LOG.error("FiscalDanfeJobScheduler falhou.", e);
      throw e;
    }
  }
}
