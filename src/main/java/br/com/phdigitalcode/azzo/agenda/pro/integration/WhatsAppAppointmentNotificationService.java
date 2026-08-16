package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;

/**
 * PLACEHOLDER — cobre o unico ponto em que {@code settings} depende de
 * {@code modules/tenant/application/WhatsAppAppointmentNotificationService.java}:
 * {@code sendCancellation(tenantId, agendamento)}, chamado por
 * {@link br.com.phdigitalcode.azzo.agenda.pro.service.SpecialClosureService} quando um fechamento e
 * confirmado com {@code notifyClients = true}.
 *
 * <p><b>Perde apenas efeito colateral</b>, na categoria de {@code AuditService}/
 * {@code EmailJobService} — nao suprime regra de negocio. E, na pratica, hoje nem chega a ser
 * chamado: o unico caminho HTTP ({@code POST /api/v1/salon/closures/confirm}) usa a sobrecarga de
 * dois argumentos, que delega com {@code notifyClients = false}. Quem passa {@code true} e o
 * {@code publicbooking}/automacao, ainda nao migrado.
 *
 * <p>No original o envio usa o template {@code DEFAULT_CANCELLATION} configurado pelo tenant, que
 * por decisao de LGPD <b>nao</b> menciona o motivo do fechamento nem dados do profissional — o
 * cliente recebe apenas data, horario e telefone de contato. Preservar isso ao trocar pelo service
 * real junto com o modulo {@code tenant}/{@code chat}.
 */
@Service
public class WhatsAppAppointmentNotificationService {

  private static final Logger LOG =
      LoggerFactory.getLogger(WhatsAppAppointmentNotificationService.class);

  /** LGPD: nao loga nome nem telefone do cliente — apenas identificadores. */
  public void sendCancellation(UUID tenantId, Agendamento agendamento) {
    logNaoEnviado("cancelamento", tenantId, agendamento);
  }

  /**
   * Confirmacao enviada ao cliente logo apos a criacao do agendamento. No original a chamada ja
   * vem envolvida em {@code try/catch} vazio no {@code ServicoAgendamentos.criar} — falha de envio
   * nunca aborta a criacao.
   */
  public void sendConfirmation(UUID tenantId, Agendamento agendamento) {
    logNaoEnviado("confirmacao", tenantId, agendamento);
  }

  /**
   * Aviso de nao comparecimento. Assinatura de um argumento so, como no original — o tenant e
   * lido do proprio agendamento.
   */
  public void sendNoShow(Agendamento agendamento) {
    logNaoEnviado("no-show", agendamento != null ? agendamento.getTenantId() : null, agendamento);
  }

  private void logNaoEnviado(String tipo, UUID tenantId, Agendamento agendamento) {
    LOG.warn(
        "Notificacao de {} por WhatsApp NAO enviada (modulo tenant/chat pendente de migracao)"
            + " tenantId={} appointmentId={}",
        tipo,
        tenantId,
        agendamento != null ? agendamento.getId() : null);
  }
}
