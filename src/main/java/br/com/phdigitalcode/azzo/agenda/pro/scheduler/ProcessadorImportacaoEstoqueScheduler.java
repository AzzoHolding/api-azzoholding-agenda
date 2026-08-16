package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ProcessadorImportacaoEstoqueService;

/**
 * Espelha
 * {@code modules/inventory/infrastructure/scheduler/ProcessadorImportacaoEstoqueScheduler.java}
 * ({@code @Scheduled(every = "20m", delayed = "70s"/"110s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina.
 */
@Component
public class ProcessadorImportacaoEstoqueScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessadorImportacaoEstoqueScheduler.class);

  private final ProcessadorImportacaoEstoqueService processadorImportacaoEstoqueService;

  public ProcessadorImportacaoEstoqueScheduler(
      ProcessadorImportacaoEstoqueService processadorImportacaoEstoqueService) {
    this.processadorImportacaoEstoqueService = processadorImportacaoEstoqueService;
  }

  @Scheduled(fixedDelayString = "PT20M", initialDelayString = "PT70S")
  void processarFilaImportacaoEstoque() {
    try {
      processadorImportacaoEstoqueService.processarFilaImportacaoEstoque();
    } catch (RuntimeException exception) {
      LOG.error("ProcessadorImportacaoEstoqueScheduler falhou.", exception);
      throw exception;
    }
  }

  @Scheduled(fixedDelayString = "PT20M", initialDelayString = "PT110S")
  void limparJobsExpiradosImportacaoEstoque() {
    try {
      processadorImportacaoEstoqueService.limparJobsExpiradosImportacaoEstoque();
    } catch (RuntimeException exception) {
      LOG.error("LimpezaImportacaoEstoqueScheduler falhou.", exception);
      throw exception;
    }
  }
}
