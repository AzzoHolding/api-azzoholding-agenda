package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ReminderProcessingService;

/**
 * Espelha {@code modules/scheduling/infrastructure/scheduler/ReminderScheduler.java}: dois
 * disparos independentes, {@code sendReminders} ({@code @Scheduled(every = "15m", delayed =
 * "80s", concurrentExecution = SKIP)}) e {@code sendHoursBeforeReminders} ({@code @Scheduled(every
 * = "15m", delayed = "3m", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} reproduz o {@code ConcurrentExecution.SKIP} do Quarkus, mesmo padrao ja
 * usado por {@code AppointmentNoShowScheduler}/{@code WhatsAppBookingReactivationScheduler}: a
 * proxima execucao so e agendada apos a anterior terminar.
 */
@Component
public class ReminderScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ReminderScheduler.class);

  private final ReminderProcessingService reminderProcessingService;

  public ReminderScheduler(ReminderProcessingService reminderProcessingService) {
    this.reminderProcessingService = reminderProcessingService;
  }

  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT80S")
  void sendReminders() {
    try {
      reminderProcessingService.sendReminders();
    } catch (Exception e) {
      LOG.error("ReminderScheduler falhou.", e);
      throw e;
    }
  }

  @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT3M")
  void sendHoursBeforeReminders() {
    try {
      reminderProcessingService.sendHoursBeforeReminders();
    } catch (Exception e) {
      LOG.error("ReminderScheduler (horas antes) falhou.", e);
      throw e;
    }
  }
}
