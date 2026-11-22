package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeVales;

/**
 * Uma vez por dia, de madrugada, marca como VENCIDO o vale-presente ativo que passou da validade (item 6). O saldo
 * que sobrou fica no extrato; o dono pode prorrogar.
 */
@Component
public class ExpiracaoDeValesScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiracaoDeValesScheduler.class);

  private final ServicoDeVales vales;

  public ExpiracaoDeValesScheduler(ServicoDeVales vales) {
    this.vales = vales;
  }

  @Scheduled(cron = "${app.vales.expiracao.cron:0 20 3 * * *}", zone = "America/Sao_Paulo")
  void expirar() {
    try {
      int vencidos = vales.expirarVencidos();
      if (vencidos > 0) LOG.info("vales_presente_vencidos quantidade={}", vencidos);
    } catch (Exception e) {
      LOG.error("vales_presente_expiracao_falhou", e);
    }
  }
}
