package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.WhatsAppBookingReactivationSchedulerService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Espelha {@code modules/chat/infrastructure/scheduler/WhatsAppBookingReactivationScheduler.java}:
 * o corpo delega ao service e uma excecao inesperada e repropagada (nao engolida), mesmo contrato
 * do original ({@code throw e} apos logar).
 */
class WhatsAppBookingReactivationSchedulerTest {

  private WhatsAppBookingReactivationSchedulerService schedulerService;
  private WhatsAppBookingReactivationScheduler scheduler;

  @BeforeEach
  void setUp() {
    schedulerService = mock(WhatsAppBookingReactivationSchedulerService.class);
    scheduler = new WhatsAppBookingReactivationScheduler(schedulerService, new SimpleMeterRegistry());
    scheduler.registerMetrics();
  }

  @Test
  void processDueCyclesDelegaAoService() {
    when(schedulerService.processDueCycles()).thenReturn(3);

    scheduler.processDueCycles();

    verify(schedulerService, times(1)).processDueCycles();
  }

  @Test
  void excecaoDoServiceEhRepropagada() {
    when(schedulerService.processDueCycles()).thenThrow(new RuntimeException("falha no ciclo"));

    assertThatThrownBy(() -> scheduler.processDueCycles())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha no ciclo");
  }
}
