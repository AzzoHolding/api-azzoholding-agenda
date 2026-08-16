package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ClientMembershipMaintenanceService;

/**
 * Espelha {@code modules/membership/infrastructure/scheduler/ClientMembershipCancellationScheduler.java}
 * ({@code @Scheduled(every = "1h", delayed = "2m", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP} do
 * Quarkus: a proxima execucao so e agendada depois que a anterior termina, entao nunca ha duas
 * rodadas simultaneas.
 */
@Component
public class ClientMembershipCancellationScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(ClientMembershipCancellationScheduler.class);

  private final ClientMembershipMaintenanceService clientMembershipMaintenanceService;

  public ClientMembershipCancellationScheduler(
      ClientMembershipMaintenanceService clientMembershipMaintenanceService) {
    this.clientMembershipMaintenanceService = clientMembershipMaintenanceService;
  }

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
  void efetivarCancelamentosPendentes() {
    try {
      int total = clientMembershipMaintenanceService.efetivarCancelamentosPendentes();
      if (total > 0) {
        LOG.info("ClientMembershipCancellationScheduler cancelou {} assinatura(s).", total);
      }
    } catch (Exception e) {
      LOG.error("ClientMembershipCancellationScheduler falhou.", e);
      throw e;
    }
  }
}
