package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDoConectorTef;

/**
 * Venda TEF que o Azzo Conector nao respondeu em 10 minutos (computador desligado, Conector
 * fechado) expira: a comanda nao fica presa em "aguardando no pinpad" e o caixa pode tentar de novo.
 */
@Component
public class VendasTefSemRespostaScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(VendasTefSemRespostaScheduler.class);

  private final ServicoDoConectorTef conector;

  public VendasTefSemRespostaScheduler(ServicoDoConectorTef conector) {
    this.conector = conector;
  }

  @Scheduled(fixedDelayString = "${app.pagamentos.tef.expirar-interval-ms:60000}", initialDelayString = "${app.pagamentos.tef.expirar-initial-delay-ms:90000}")
  void expirar() {
    try {
      int n = conector.expirarSemResposta();
      if (n > 0) LOG.info("tef_vendas_expiradas quantidade={}", n);
    } catch (Exception e) {
      LOG.error("tef_expirar_falhou", e);
    }
  }
}
