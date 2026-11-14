package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDePontos;

/**
 * Uma vez por dia, de madrugada, vence os pontos de fidelidade com a validade passada (a validade e
 * definida pelo dono de cada salao; sem validade, nada vence). Em lotes, ate nao sobrar vencido.
 */
@Component
public class ExpiracaoDePontosScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ExpiracaoDePontosScheduler.class);
  private static final int LOTE = 500;

  private final ServicoDePontos pontos;

  public ExpiracaoDePontosScheduler(ServicoDePontos pontos) {
    this.pontos = pontos;
  }

  @Scheduled(cron = "${app.fidelidade.expiracao.cron:0 10 3 * * *}", zone = "America/Sao_Paulo")
  void expirar() {
    try {
      int total = 0;
      for (int rodada = 0; rodada < 100; rodada++) {
        int vencidos = pontos.expirarVencidos(LOTE);
        total += vencidos;
        if (vencidos < LOTE) break;
      }
      if (total > 0) LOG.info("fidelidade_pontos_vencidos creditos={}", total);
    } catch (Exception e) {
      LOG.error("fidelidade_expiracao_falhou", e);
    }
  }
}
