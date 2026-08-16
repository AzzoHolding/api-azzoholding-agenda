package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.AppointmentDepositMaintenanceService;

/**
 * Espelha
 * {@code modules/scheduling/infrastructure/scheduler/AppointmentDepositExpirationScheduler.java}
 * ({@code @Scheduled(every = "5m", delayed = "30s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina.
 */
@Component
public class AppointmentDepositExpirationScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(AppointmentDepositExpirationScheduler.class);

  private final AppointmentDepositMaintenanceService appointmentDepositMaintenanceService;

  public AppointmentDepositExpirationScheduler(
      AppointmentDepositMaintenanceService appointmentDepositMaintenanceService) {
    this.appointmentDepositMaintenanceService = appointmentDepositMaintenanceService;
  }

  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT30S")
  void expirarSinaisPendentes() {
    try {
      int total = appointmentDepositMaintenanceService.expirarSinaisPendentes();
      if (total > 0) {
        LOG.info(
            "AppointmentDepositExpirationScheduler expirou {} sinal(is) de reserva.", total);
      }
    } catch (Exception e) {
      LOG.error("AppointmentDepositExpirationScheduler falhou.", e);
      throw e;
    }
  }
}
