package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.CheckoutIntentMaintenanceService;

/**
 * Espelha {@code modules/billing/infrastructure/scheduler/CheckoutIntentExpirationScheduler.java}
 * ({@code @Scheduled(every = "1m", delayed = "20s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina.
 */
@Component
public class CheckoutIntentExpirationScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(CheckoutIntentExpirationScheduler.class);

  private final CheckoutIntentMaintenanceService checkoutIntentMaintenanceService;

  public CheckoutIntentExpirationScheduler(
      CheckoutIntentMaintenanceService checkoutIntentMaintenanceService) {
    this.checkoutIntentMaintenanceService = checkoutIntentMaintenanceService;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT20S")
  void expireIntents() {
    try {
      long updated = checkoutIntentMaintenanceService.expirePendingIntents();
      if (updated > 0) {
        LOG.info("CheckoutIntentExpirationScheduler expirou {} intent(s).", updated);
      }
    } catch (Exception e) {
      LOG.error("CheckoutIntentExpirationScheduler falhou.", e);
      throw e;
    }
  }
}
