package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.EstadoImportacaoClientes;
import br.com.phdigitalcode.azzo.agenda.pro.service.ProcessadorImportacaoClientesService;

/**
 * Espelha {@code modules/customers/infrastructure/scheduler/ProcessadorImportacaoClientesScheduler.java}
 * ({@code @Scheduled(every = "3m", delayed = "30s", concurrentExecution = SKIP)}).
 *
 * <p>{@code fixedDelay} (e nao {@code fixedRate}) reproduz o {@code ConcurrentExecution.SKIP}: a
 * proxima execucao so e agendada depois que a anterior termina.
 */
@Component
public class ProcessadorImportacaoClientesScheduler {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessadorImportacaoClientesScheduler.class);

  private final ProcessadorImportacaoClientesService processadorImportacaoClientesService;
  private final EstadoImportacaoClientes estadoImportacaoClientes;
  private final int limitePorRodada;

  public ProcessadorImportacaoClientesScheduler(
      ProcessadorImportacaoClientesService processadorImportacaoClientesService,
      EstadoImportacaoClientes estadoImportacaoClientes,
      @Value("${app.clientes.importacao.limite-por-rodada:50}") int limitePorRodada) {
    this.processadorImportacaoClientesService = processadorImportacaoClientesService;
    this.estadoImportacaoClientes = estadoImportacaoClientes;
    this.limitePorRodada = limitePorRodada;
  }

  @Scheduled(fixedDelayString = "PT3M", initialDelayString = "PT30S")
  void processarFilaImportacaoClientes() {
    Instant execucao = Instant.now();
    try {
      int processados = processadorImportacaoClientesService.processarFila(Math.max(limitePorRodada, 1));
      estadoImportacaoClientes.registrarProcessamento(execucao, processados, 0);
      LOG.info("ProcessadorImportacaoClientesScheduler finalizado. processados={}", processados);
    } catch (RuntimeException exception) {
      estadoImportacaoClientes.registrarFalhaProcessamento(exception.getMessage());
      LOG.error("ProcessadorImportacaoClientesScheduler falhou.", exception);
      throw exception;
    }
  }
}
