package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.phdigitalcode.azzo.agenda.pro.integration.EmailJobService;

/**
 * Cobre {@code EmailJobScheduler} (espelha {@code modules/email/infrastructure/scheduler/EmailJobScheduler.java}).
 *
 * <p>O ponto central e o guard manual ({@code AtomicBoolean}): como {@code processPendingJobsAsync}
 * despacha trabalho assincrono e retorna na hora, uma segunda chamada enquanto a rodada anterior
 * ainda "esta em execucao" (onComplete nao disparou) deve ser ignorada.
 */
@ExtendWith(MockitoExtension.class)
class EmailJobSchedulerTest {

  @Mock private EmailJobService emailJobService;

  private EmailJobScheduler scheduler;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    scheduler = new EmailJobScheduler(emailJobService);
  }

  @Test
  void processarFilaChamaServicoQuandoNaoHaRodadaEmExecucao() {
    when(emailJobService.processPendingJobsAsync(any())).thenReturn(0);

    scheduler.processarFila();

    verify(emailJobService, times(1)).processPendingJobsAsync(any());
  }

  @Test
  void processarFilaIgnoraSegundaChamadaEnquantoRodadaAnteriorNaoCompletou() {
    // onComplete nunca invocado aqui -> simula processamento assincrono ainda em andamento.
    when(emailJobService.processPendingJobsAsync(any())).thenReturn(5);

    scheduler.processarFila();
    scheduler.processarFila();

    verify(emailJobService, times(1)).processPendingJobsAsync(any());
  }

  @Test
  void processarFilaPermiteNovaRodadaAposOnCompleteDaAnterior() {
    ArgumentCaptor<Runnable> onCompleteCaptor = ArgumentCaptor.forClass(Runnable.class);
    when(emailJobService.processPendingJobsAsync(onCompleteCaptor.capture())).thenReturn(3);

    scheduler.processarFila();
    onCompleteCaptor.getValue().run();
    scheduler.processarFila();

    verify(emailJobService, times(2)).processPendingJobsAsync(any());
  }

  @Test
  void processarFilaLiberaGuardImediatamenteQuandoNaoHaJobsProcessados() {
    when(emailJobService.processPendingJobsAsync(any())).thenReturn(0);

    scheduler.processarFila();
    scheduler.processarFila();

    verify(emailJobService, times(2)).processPendingJobsAsync(any());
  }

  @Test
  void processarFilaLiberaGuardQuandoServicoLancaExcecao() {
    when(emailJobService.processPendingJobsAsync(any())).thenThrow(new RuntimeException("falha"));

    try {
      scheduler.processarFila();
    } catch (RuntimeException ignored) {
      // esperado: excecao repropagada
    }
    // doReturn (nao when/thenReturn): o stub anterior lanca excecao, e when(mock.foo()) chamaria
    // foo() de verdade para registrar o novo stub, disparando a excecao antes de reconfigurar.
    org.mockito.Mockito.doReturn(0).when(emailJobService).processPendingJobsAsync(any());
    scheduler.processarFila();

    verify(emailJobService, times(2)).processPendingJobsAsync(any());
  }
}
