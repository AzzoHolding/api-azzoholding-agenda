package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.NfsePdfJobService;

/**
 * Espelha {@code modules/nfse/infrastructure/scheduler/NfsePdfJobScheduler.java}
 * ({@code @Scheduled(every = "20s", delayed = "40s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina — mesmo padrao ja usado em
 * {@link br.com.phdigitalcode.azzo.agenda.pro.scheduler.FiscalDanfeJobScheduler} (Fronteira 5 de
 * {@code fiscal}).
 */
@Component
public class NfsePdfJobScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(NfsePdfJobScheduler.class);

  private final NfsePdfJobService nfsePdfJobService;

  public NfsePdfJobScheduler(NfsePdfJobService nfsePdfJobService) {
    this.nfsePdfJobService = nfsePdfJobService;
  }

  @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT40S")
  void processarFilaPdfNfse() {
    try {
      int processados = nfsePdfJobService.processarPendentes();
      if (processados > 0) {
        LOG.info("NFS-e PDF scheduler processou {} job(s).", processados);
      }
    } catch (RuntimeException e) {
      LOG.error("NfsePdfJobScheduler falhou.", e);
      throw e;
    }
  }
}
