package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.LgpdRetentionService;

/**
 * Espelha {@code scheduler/LgpdRetentionScheduler.java} ({@code @Scheduled(cron =
 * "{app.lgpd.retention.purge-cron:0 30 2 * * ?}", concurrentExecution = SKIP)}). Como nos demais
 * schedulers ja portados, {@code SKIP} nao precisa de tratamento explicito: o pool padrao do
 * {@code @Scheduled} do Spring e single-threaded.
 */
@Component
public class LgpdRetentionScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(LgpdRetentionScheduler.class);

  private final LgpdRetentionService lgpdRetentionService;

  public LgpdRetentionScheduler(LgpdRetentionService lgpdRetentionService) {
    this.lgpdRetentionService = lgpdRetentionService;
  }

  @Scheduled(cron = "${app.lgpd.retention.purge-cron:0 30 2 * * ?}")
  void purgarDadosExpirados() {
    LOG.info("LGPD retention job iniciado.");
    try {
      lgpdRetentionService.logDadosExpirados();
      lgpdRetentionService.purgarDadosExpirados();
    } catch (Exception e) {
      LOG.error("LGPD retention job falhou.", e);
      throw e;
    }
  }
}
