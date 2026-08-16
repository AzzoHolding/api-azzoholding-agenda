package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.NfseOperationalAlertService;

/**
 * Espelha {@code modules/nfse/infrastructure/scheduler/NfseOperationalAlertScheduler.java}
 * ({@code @Scheduled(every = "30m", delayed = "100s", concurrentExecution = SKIP)}).
 */
@Component
public class NfseOperationalAlertScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(NfseOperationalAlertScheduler.class);

  private final NfseOperationalAlertService nfseOperationalAlertService;

  public NfseOperationalAlertScheduler(NfseOperationalAlertService nfseOperationalAlertService) {
    this.nfseOperationalAlertService = nfseOperationalAlertService;
  }

  @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT100S")
  void analisarAlertasOperacionaisNfse() {
    try {
      nfseOperationalAlertService.analisarAlertasOperacionaisNfse();
    } catch (RuntimeException e) {
      LOG.error("NfseOperationalAlertScheduler falhou.", e);
      throw e;
    }
  }
}
