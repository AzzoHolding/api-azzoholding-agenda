package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoLiberacaoDeDocumentoDeTrial;

/**
 * Solta o documento do periodo gratuito de cadastros nunca confirmados (achado SEG-009).
 *
 * <p>{@code fixedDelay}: a proxima execucao so e agendada depois que a anterior termina. A liberacao
 * e idempotente (um UPDATE condicional), entao varias instancias rodando juntas nao fazem mal.
 */
@Component
public class LiberacaoDeDocumentoDeTrialScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(LiberacaoDeDocumentoDeTrialScheduler.class);

  private final ServicoLiberacaoDeDocumentoDeTrial servico;

  public LiberacaoDeDocumentoDeTrialScheduler(ServicoLiberacaoDeDocumentoDeTrial servico) {
    this.servico = servico;
  }

  @Scheduled(
      fixedDelayString = "${app.registration.release-interval:PT6H}",
      initialDelayString = "PT10M")
  void liberarDocumentos() {
    try {
      servico.liberar();
    } catch (Exception e) {
      LOG.error("LiberacaoDeDocumentoDeTrialScheduler falhou.", e);
      throw e;
    }
  }
}
