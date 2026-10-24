package br.com.phdigitalcode.azzo.agenda.pro.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoTemplatesDoWhatsapp;

/**
 * Reconfere na Meta o estado dos templates pendentes.
 *
 * <p><b>Criar um template nao e te-lo aprovado.</b> A Meta analisa depois, leva de minutos a
 * horas, e nao avisa: o unico aviso seria o webhook {@code message_template_status_update}, que
 * depende de o webhook estar recebendo — e em 2026-09-22 ele nao estava. Perguntar funciona de
 * qualquer jeito, e e o que garante que um salao descubra um template RECUSADO antes de um
 * cliente nao receber a confirmacao.
 *
 * <p>A cada 10 minutos, e so para os pendentes: aprovado nao se reconfere.
 */
@Component
public class WhatsAppTemplateStatusScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(WhatsAppTemplateStatusScheduler.class);

  private final ServicoTemplatesDoWhatsapp servicoTemplates;

  public WhatsAppTemplateStatusScheduler(ServicoTemplatesDoWhatsapp servicoTemplates) {
    this.servicoTemplates = servicoTemplates;
  }

  @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT90S")
  public void conferirTemplatesPendentes() {
    try {
      int mudaram = servicoTemplates.sincronizarPendentes();
      if (mudaram > 0) {
        LOG.info("WhatsAppTemplateStatus finalizado. templatesAtualizados={}", mudaram);
      }
    } catch (Exception erro) {
      // O agendador nao pode morrer por causa de uma rodada ruim.
      LOG.warn("WhatsAppTemplateStatus falhou: {}", erro.getMessage());
    }
  }
}
