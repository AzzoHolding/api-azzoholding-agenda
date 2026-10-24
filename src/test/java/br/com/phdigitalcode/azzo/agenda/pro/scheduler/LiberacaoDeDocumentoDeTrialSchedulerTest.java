package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoLiberacaoDeDocumentoDeTrial;

class LiberacaoDeDocumentoDeTrialSchedulerTest {

  @Test
  void aCadaExecucaoChamaOServico() {
    ServicoLiberacaoDeDocumentoDeTrial servico = mock(ServicoLiberacaoDeDocumentoDeTrial.class);

    new LiberacaoDeDocumentoDeTrialScheduler(servico).liberarDocumentos();

    verify(servico).liberar();
  }

  /** Falha vai para o log e sobe, como os demais agendadores: nao some em silencio. */
  @Test
  void falhaDoServicoNaoEEngolida() {
    ServicoLiberacaoDeDocumentoDeTrial servico = mock(ServicoLiberacaoDeDocumentoDeTrial.class);
    when(servico.liberar()).thenThrow(new IllegalStateException("banco fora"));

    assertThatThrownBy(() -> new LiberacaoDeDocumentoDeTrialScheduler(servico).liberarDocumentos())
        .isInstanceOf(IllegalStateException.class);
  }
}
