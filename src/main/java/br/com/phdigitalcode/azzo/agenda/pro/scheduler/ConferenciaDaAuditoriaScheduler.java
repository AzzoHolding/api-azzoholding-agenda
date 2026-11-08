package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ConferenciaDaAuditoria;

/**
 * Confere a trilha de auditoria de todos os saloes uma vez por noite, depois do expurgo (03:30).
 * Violacao sai no log ({@code audit_chain_violation}) e na metrica {@code audit.chain.violations}.
 */
@Component
public class ConferenciaDaAuditoriaScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ConferenciaDaAuditoriaScheduler.class);

  private final ConferenciaDaAuditoria conferencia;

  public ConferenciaDaAuditoriaScheduler(ConferenciaDaAuditoria conferencia) {
    this.conferencia = conferencia;
  }

  @Scheduled(cron = "${app.audit.chain-verification.cron:0 15 4 * * ?}", zone = "America/Sao_Paulo")
  void conferir() {
    try {
      conferencia.conferirTodos("SCHEDULER");
    } catch (Exception e) {
      LOG.error("audit_chain_verification_scheduler_failed", e);
    }
  }
}
