package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.Agendamento;
import br.com.phdigitalcode.azzo.agenda.pro.service.AfterCommitExecutor;
import br.com.phdigitalcode.azzo.agenda.pro.service.ServicoConfirmacaoDeAgendamento;

/**
 * Avisos de WhatsApp ao cliente sobre o agendamento dele.
 *
 * <p>{@code sendConfirmation} envia de verdade desde 2026-09-24. {@code sendCancellation} envia de
 * verdade desde 2026-10-06 (achado AGD-008 da auditoria). {@code sendNoShow} <b>continua so
 * logando</b>, de proposito: nao existe template de no-show (as finalidades sao confirmacao,
 * cancelamento e lembrete) e avisar o cliente de que ele faltou e decisao de produto.
 *
 * <p>{@code sendCancellation(tenantId, agendamento)} e chamado quando o salao cancela um horario
 * ({@code ServicoAgendamentos}) e por {@link
 * br.com.phdigitalcode.azzo.agenda.pro.service.SpecialClosureService} quando um fechamento e
 * confirmado com {@code notifyClients = true} (hoje alcancavel por
 * {@code POST /api/v1/salon/closures/confirm?notifyClients=true}). Antes, nos dois casos o cliente
 * era cancelado e nao recebia nada.
 *
 * <p>LGPD: o aviso de cancelamento nunca carrega o motivo do cancelamento ou do fechamento (nao ha
 * variavel de motivo no vocabulario do template).
 */
@Service
public class WhatsAppAppointmentNotificationService {

  private static final Logger LOG =
      LoggerFactory.getLogger(WhatsAppAppointmentNotificationService.class);

  private final ServicoConfirmacaoDeAgendamento servicoConfirmacao;
  private final AfterCommitExecutor afterCommitExecutor;

  public WhatsAppAppointmentNotificationService(
      ServicoConfirmacaoDeAgendamento servicoConfirmacao, AfterCommitExecutor afterCommitExecutor) {
    this.servicoConfirmacao = servicoConfirmacao;
    this.afterCommitExecutor = afterCommitExecutor;
  }

  /**
   * Avisa o cliente de que o horario dele foi cancelado.
   *
   * <p><b>Sai DEPOIS do commit</b>, em thread de fundo ({@link AfterCommitExecutor}): os dois
   * chamadores (mudanca de status e fechamento especial) rodam dentro da transacao do cancelamento,
   * e mandar na hora avisaria de um cancelamento que depois sofre rollback — o cliente perderia um
   * horario que ainda existe. Sem transacao ativa, roda de imediato (tambem em fundo). Nunca lanca
   * para quem chama: o cancelamento ja aconteceu, e falha de envio so entra no log.
   *
   * <p>LGPD: nao loga nome nem telefone do cliente — apenas identificadores.
   */
  public void sendCancellation(UUID tenantId, Agendamento agendamento) {
    if (tenantId == null || agendamento == null) return;
    afterCommitExecutor.run(() -> servicoConfirmacao.enviarCancelamento(tenantId, agendamento));
  }

  /**
   * Confirmacao enviada ao cliente logo apos a criacao do agendamento.
   *
   * <p><b>Deixou de ser placeholder em 2026-09-24.</b> Ate entao so logava: o cliente marcava um
   * horario e nao recebia nada. Sai por template aprovado, porque confirmacao de cliente novo e
   * sempre primeiro contato — e fora da janela de 24h o texto livre e aceito e descartado.
   *
   * <p><b>Sai DEPOIS do commit</b> ({@link AfterCommitExecutor}), como o cancelamento. A chamada ja
   * vem envolvida em {@code try/catch} no {@code ServicoAgendamentos.criar}, e o proprio servico nao
   * lanca: falha de envio nunca aborta a criacao.
   */
  public void sendConfirmation(UUID tenantId, Agendamento agendamento) {
    if (tenantId == null || agendamento == null) return;
    // DEPOIS do commit, em thread de fundo (achado INT-007, auditoria de 2026-10-06). Antes o envio
    // rodava dentro de ServicoAgendamentos.criar, com o advisory lock do profissional/dia e uma
    // conexao do pool presos durante a chamada a Meta (ate 10s + 20s de timeout): uma Meta lenta
    // travava todas as reservas daquele profissional naquele dia. E, se o commit falhasse depois, o
    // cliente ja tinha recebido a confirmacao de um agendamento que nao existe.
    afterCommitExecutor.run(() -> servicoConfirmacao.enviar(tenantId, agendamento));
  }

  /**
   * Aviso de nao comparecimento. Assinatura de um argumento so, como no original — o tenant e
   * lido do proprio agendamento.
   *
   * <p><b>Continua sem enviar</b>: nao ha template de no-show e a decisao de avisar o cliente de
   * que faltou e de produto (AGD-008). So registra no log, com identificadores.
   */
  public void sendNoShow(Agendamento agendamento) {
    LOG.info(
        "Aviso de no-show por WhatsApp nao enviado (sem template de no-show; decisao de produto)"
            + " tenantId={} appointmentId={}",
        agendamento != null ? agendamento.getTenantId() : null,
        agendamento != null ? agendamento.getId() : null);
  }
}
