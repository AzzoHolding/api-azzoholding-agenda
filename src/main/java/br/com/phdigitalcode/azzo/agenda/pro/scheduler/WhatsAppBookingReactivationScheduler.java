package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.WhatsAppBookingReactivationSchedulerService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;

/**
 * Espelha {@code modules/chat/infrastructure/scheduler/WhatsAppBookingReactivationScheduler.java}
 * ({@code @Scheduled(every = "15m", delayed = "90s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} reproduz o {@code ConcurrentExecution.SKIP} do Quarkus, mesmo padrao ja
 * usado por {@code AppointmentNoShowScheduler}/{@code CheckoutIntentExpirationScheduler}: a
 * proxima execucao so e agendada apos a anterior terminar.
 *
 * <p>A gauge {@code reactivation_scheduler_last_success_epoch_seconds} e portada verbatim (mesmo
 * nome e descricao) — o {@code MeterRegistry} do Spring Boot Actuator/Micrometer e injetavel aqui
 * exatamente como no Quarkus.
 */
@Component
public class WhatsAppBookingReactivationScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(WhatsAppBookingReactivationScheduler.class);

  private final WhatsAppBookingReactivationSchedulerService whatsAppBookingReactivationSchedulerService;
  private final MeterRegistry meterRegistry;

  // Epoch-seconds do ultimo ciclo bem-sucedido; 0 = nunca executou nesta instancia
  private final AtomicLong lastSuccessEpoch = new AtomicLong(0);

  public WhatsAppBookingReactivationScheduler(
      WhatsAppBookingReactivationSchedulerService whatsAppBookingReactivationSchedulerService,
      MeterRegistry meterRegistry) {
    this.whatsAppBookingReactivationSchedulerService = whatsAppBookingReactivationSchedulerService;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  void registerMetrics() {
    Gauge.builder("reactivation_scheduler_last_success_epoch_seconds", lastSuccessEpoch, AtomicLong::get)
        .description("Epoch-seconds do ultimo ciclo de reativacao bem-sucedido")
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT90S")
  void processDueCycles() {
    try {
      whatsAppBookingReactivationSchedulerService.processDueCycles();
      lastSuccessEpoch.set(System.currentTimeMillis() / 1000);
    } catch (Exception e) {
      LOG.error("WhatsAppBookingReactivationScheduler falhou.", e);
      throw e;
    }
  }
}
