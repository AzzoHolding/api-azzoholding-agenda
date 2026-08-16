package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoFinanceiro;

/**
 * Espelha {@code scheduler/RecurringTransactionScheduler.java}. Roda diariamente as 01:00
 * (horario de Brasilia) e gera os lancamentos financeiros de todos os templates recorrentes ativos.
 *
 * <p>Idempotente: se rodar duas vezes no mesmo dia, nao cria duplicatas (a checagem esta em
 * {@code ServicoFinanceiro.gerarLancamentosRecorrentes}).
 *
 * <p>A {@code timeZone} do {@code @Scheduled} do Quarkus vira {@code zone} no Spring — o
 * inventario registrou que o cron original nao tinha timezone explicito em varios schedulers, mas
 * <b>este tinha</b> ({@code America/Sao_Paulo}) e foi preservado.
 */
@Component
public class RecurringTransactionScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(RecurringTransactionScheduler.class);

  private final ServicoFinanceiro servicoFinanceiro;

  public RecurringTransactionScheduler(ServicoFinanceiro servicoFinanceiro) {
    this.servicoFinanceiro = servicoFinanceiro;
  }

  @Scheduled(cron = "0 0 1 * * ?", zone = "America/Sao_Paulo")
  void gerarLancamentosRecorrentes() {
    LOG.info("Iniciando geracao de lancamentos recorrentes...");
    try {
      servicoFinanceiro.gerarLancamentosRecorrentes();
      LOG.info("Lancamentos recorrentes gerados com sucesso.");
    } catch (Exception e) {
      LOG.error("Erro ao gerar lancamentos recorrentes", e);
    }
  }
}
