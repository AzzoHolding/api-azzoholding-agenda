package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoDeAssinaturasNoCartao;

/**
 * A reserva do aviso de assinatura (Fase 7): de 10 em 10 minutos, o clube no cartao recorrente que
 * espera o cartao ou esta com o periodo vencendo e consultado na operadora. Sem isto, um aviso
 * perdido deixava a cliente sem os beneficios do mes pago.
 */
@Component
public class ConsultaDeAssinaturasNoCartaoScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ConsultaDeAssinaturasNoCartaoScheduler.class);

  private final ServicoDeAssinaturasNoCartao assinaturas;

  public ConsultaDeAssinaturasNoCartaoScheduler(ServicoDeAssinaturasNoCartao assinaturas) {
    this.assinaturas = assinaturas;
  }

  @Scheduled(fixedDelayString = "${app.pagamentos.assinaturas.interval-ms:600000}", initialDelayString = "${app.pagamentos.assinaturas.initial-delay-ms:120000}")
  void consultar() {
    try {
      int mudaram = assinaturas.consultarPendentes();
      if (mudaram > 0) LOG.info("clube_cartao_consulta_de_reserva mudaram={}", mudaram);
    } catch (Exception e) {
      LOG.error("clube_cartao_consulta_de_reserva_falhou", e);
    }
  }
}
