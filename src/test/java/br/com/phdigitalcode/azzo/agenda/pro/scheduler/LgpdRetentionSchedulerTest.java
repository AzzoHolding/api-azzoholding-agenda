package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.LgpdRetentionService;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoEliminacaoFimDeContrato;

/**
 * Espelha {@code scheduler/LgpdRetentionScheduler.java}: chama {@code logDadosExpirados} antes de
 * {@code purgarDadosExpirados}, e uma excecao inesperada e repropagada, mesmo contrato do original.
 */
class LgpdRetentionSchedulerTest {

  private LgpdRetentionService lgpdRetentionService;
  private ServicoEliminacaoFimDeContrato eliminacao;
  private LgpdRetentionScheduler scheduler;

  @BeforeEach
  void setUp() {
    lgpdRetentionService = mock(LgpdRetentionService.class);
    eliminacao = mock(ServicoEliminacaoFimDeContrato.class);
    scheduler = new LgpdRetentionScheduler(lgpdRetentionService, eliminacao);
  }

  @Test
  void purgarDadosExpiradosChamaLogAntesDaPurga() {
    scheduler.purgarDadosExpirados();

    org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(lgpdRetentionService);
    ordem.verify(lgpdRetentionService, times(1)).logDadosExpirados();
    ordem.verify(lgpdRetentionService, times(1)).purgarDadosExpirados();
    verify(eliminacao).executar();
  }

  @Test
  void excecaoDoLogEhRepropagada() {
    doThrow(new RuntimeException("falha ao logar")).when(lgpdRetentionService).logDadosExpirados();

    assertThatThrownBy(() -> scheduler.purgarDadosExpirados())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha ao logar");
    verify(lgpdRetentionService, times(0)).purgarDadosExpirados();
  }

  @Test
  void excecaoDaPurgaEhRepropagada() {
    doThrow(new RuntimeException("falha na purga")).when(lgpdRetentionService).purgarDadosExpirados();

    assertThatThrownBy(() -> scheduler.purgarDadosExpirados())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("falha na purga");
  }
}
