package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDePagamentos;

/**
 * A reserva do webhook: cobranca esperando resposta ha mais de 20 s (e menos de 2 h) e consultada
 * na operadora. Sem isto, um aviso perdido deixava a comanda presa em "aguardando".
 */
@Component
public class ConsultaDeCobrancasScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ConsultaDeCobrancasScheduler.class);

  private final ServicoDePagamentos pagamentos;

  public ConsultaDeCobrancasScheduler(ServicoDePagamentos pagamentos) {
    this.pagamentos = pagamentos;
  }

  @Scheduled(fixedDelayString = "${app.pagamentos.consulta.interval-ms:30000}", initialDelayString = "${app.pagamentos.consulta.initial-delay-ms:60000}")
  void consultar() {
    try {
      int atualizadas = pagamentos.consultarPendentes();
      if (atualizadas > 0) LOG.info("pagamentos_consulta_de_reserva atualizadas={}", atualizadas);
    } catch (Exception e) {
      LOG.error("pagamentos_consulta_de_reserva_falhou", e);
    }
  }
}
