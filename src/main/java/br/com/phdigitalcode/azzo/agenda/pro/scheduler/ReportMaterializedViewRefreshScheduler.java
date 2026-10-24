package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Espelha {@code scheduler/ReportMaterializedViewRefreshScheduler.java}
 * ({@code @Scheduled(every = "5m", delayed = "120s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} reproduz o {@code SKIP}: o proximo recalculo so e agendado depois que o
 * anterior termina. Mesmo padrao de {@link AppointmentNoShowScheduler}.
 */
@Component
public class ReportMaterializedViewRefreshScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(ReportMaterializedViewRefreshScheduler.class);

  private final ReportMaterializedViewRefreshService reportMaterializedViewRefreshService;

  public ReportMaterializedViewRefreshScheduler(
      ReportMaterializedViewRefreshService reportMaterializedViewRefreshService) {
    this.reportMaterializedViewRefreshService = reportMaterializedViewRefreshService;
  }

  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT120S")
  void refreshMaterializedViews() {
    try {
      int atualizadas = reportMaterializedViewRefreshService.refreshMaterializedViews();
      if (atualizadas < ReportMaterializedViewRefreshService.VIEWS.size()) {
        LOG.warn(
            "ReportMaterializedViewRefresh: {} de {} views atualizadas.",
            atualizadas,
            ReportMaterializedViewRefreshService.VIEWS.size());
      }
    } catch (Exception e) {
      LOG.error("ReportMaterializedViewRefreshScheduler falhou.", e);
      throw e;
    }
  }
}
