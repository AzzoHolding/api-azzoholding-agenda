package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.AuditRetentionPurgeService;

/**
 * Espelha {@code modules/audit/infrastructure/scheduler/AuditRetentionPurgeScheduler.java}
 * ({@code @Scheduled(cron = "{app.audit.retention.purge.cron:0 30 3 * * ?}",
 * concurrentExecution = SKIP)}). Como nos demais schedulers ja portados, {@code SKIP} nao precisa
 * de tratamento explicito: o pool padrao do {@code @Scheduled} do Spring e single-threaded, entao
 * execucoes concorrentes da mesma tarefa nao acontecem.
 */
@Component
public class AuditRetentionPurgeScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(AuditRetentionPurgeScheduler.class);

  private final AuditRetentionPurgeService auditRetentionPurgeService;

  public AuditRetentionPurgeScheduler(AuditRetentionPurgeService auditRetentionPurgeService) {
    this.auditRetentionPurgeService = auditRetentionPurgeService;
  }

  @Scheduled(cron = "${app.audit.retention.purge.cron:0 30 3 * * ?}")
  void purgeExpiredAuditEvents() {
    try {
      long totalPurged = auditRetentionPurgeService.purgeExpiredAuditEvents();
      if (totalPurged > 0) {
        LOG.info("audit_retention_purge purged={}", totalPurged);
      }
    } catch (Exception e) {
      LOG.error("AuditRetentionPurgeScheduler falhou.", e);
      throw e;
    }
  }
}
