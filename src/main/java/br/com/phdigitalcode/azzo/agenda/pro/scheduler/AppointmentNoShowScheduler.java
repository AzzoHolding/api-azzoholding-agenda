package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.AppointmentNoShowProcessingService;

/**
 * Espelha {@code modules/scheduling/infrastructure/scheduler/AppointmentNoShowScheduler.java}
 * ({@code @Scheduled(every = "30m", delayed = "60s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina. Mesmo padrao de
 * {@link CheckoutIntentExpirationScheduler}.
 *
 * <p>O original nao declara timezone no scheduler — o fuso {@code America/Sao_Paulo} e aplicado
 * dentro do service, ao calcular a data de referencia e a janela de 6 horas.
 */
@Component
public class AppointmentNoShowScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AppointmentNoShowScheduler.class);

  private final AppointmentNoShowProcessingService appointmentNoShowProcessingService;

  public AppointmentNoShowScheduler(
      AppointmentNoShowProcessingService appointmentNoShowProcessingService) {
    this.appointmentNoShowProcessingService = appointmentNoShowProcessingService;
  }

  @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT60S")
  void markOpenAppointmentsAsNoShow() {
    try {
      int changed = appointmentNoShowProcessingService.markOpenAppointmentsAsNoShow();
      if (changed > 0) {
        LOG.info("AppointmentNoShowScheduler processou {} agendamento(s).", changed);
      }
    } catch (Exception e) {
      LOG.error("AppointmentNoShowScheduler falhou.", e);
      throw e;
    }
  }
}
