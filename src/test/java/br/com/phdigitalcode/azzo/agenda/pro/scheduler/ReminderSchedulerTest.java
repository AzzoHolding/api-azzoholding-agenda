package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.ReminderProcessingService;

/**
 * Espelha {@code modules/scheduling/infrastructure/scheduler/ReminderScheduler.java}: os dois
 * disparos delegam ao service e uma excecao inesperada e repropagada (nao engolida), mesmo
 * contrato do original ({@code throw e} apos logar).
 */
class ReminderSchedulerTest {

  private ReminderProcessingService reminderProcessingService;
  private ReminderScheduler scheduler;

  @BeforeEach
  void setUp() {
    reminderProcessingService = mock(ReminderProcessingService.class);
    scheduler = new ReminderScheduler(reminderProcessingService);
  }

  @Test
  void sendRemindersDelegaAoService() {
    when(reminderProcessingService.sendReminders()).thenReturn(3);

    scheduler.sendReminders();

    verify(reminderProcessingService, times(1)).sendReminders();
  }

  @Test
  void excecaoDoServiceEhRepropagadaNoLembreteDeVespera() {
    when(reminderProcessingService.sendReminders()).thenThrow(new RuntimeException("falha D-1"));

    assertThatThrownBy(() -> scheduler.sendReminders())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha D-1");
  }

  @Test
  void sendHoursBeforeRemindersDelegaAoService() {
    when(reminderProcessingService.sendHoursBeforeReminders()).thenReturn(2);

    scheduler.sendHoursBeforeReminders();

    verify(reminderProcessingService, times(1)).sendHoursBeforeReminders();
  }

  @Test
  void excecaoDoServiceEhRepropagadaNoLembreteDeHoras() {
    when(reminderProcessingService.sendHoursBeforeReminders())
        .thenThrow(new RuntimeException("falha horas antes"));

    assertThatThrownBy(() -> scheduler.sendHoursBeforeReminders())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha horas antes");
  }
}
