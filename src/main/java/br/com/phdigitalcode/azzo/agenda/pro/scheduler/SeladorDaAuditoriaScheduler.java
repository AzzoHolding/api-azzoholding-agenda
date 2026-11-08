package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.SeladorDaAuditoria;

/**
 * Sela a trilha de auditoria a cada poucos segundos: o evento fica sem elo so nesse intervalo. Uma
 * falha nao para nada — os eventos ficam na fila e a proxima rodada tenta de novo; a conferencia
 * acusa evento sem selo antigo.
 */
@Component
@ConditionalOnProperty(name = "app.audit.seal.enabled", havingValue = "true", matchIfMissing = true)
public class SeladorDaAuditoriaScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(SeladorDaAuditoriaScheduler.class);

  private final SeladorDaAuditoria selador;

  public SeladorDaAuditoriaScheduler(SeladorDaAuditoria selador) {
    this.selador = selador;
  }

  @Scheduled(fixedDelayString = "${app.audit.seal.interval-ms:5000}", initialDelayString = "${app.audit.seal.initial-delay-ms:20000}")
  void selar() {
    try {
      selador.selarPendentes();
    } catch (Exception e) {
      LOG.error("audit_seal_failed", e);
    }
  }
}
