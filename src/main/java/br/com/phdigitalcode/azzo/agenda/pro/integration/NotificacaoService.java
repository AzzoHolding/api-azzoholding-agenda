package br.com.phdigitalcode.azzo.agenda.pro.integration;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.util.CorrelatedLogging;

/**
 * PLACEHOLDER — cobre o unico ponto em que {@code scheduling} depende de
 * {@code modules/notifications/application/ServicoNotificacoes.java}:
 * {@code registrarCriacaoAgendamento}, chamado por {@code ServicoAgendamentos.criar}.
 *
 * <p><b>Perde apenas efeito colateral</b>, na mesma categoria de {@code AuditService} /
 * {@code EmailJobService}: a criacao do agendamento continua correta, mas o sino de notificacoes
 * internas do dono/recepcao nao recebe o item "novo agendamento". Substituir por
 * {@code ServicoNotificacoes} real ao migrar o modulo {@code notifications}.
 *
 * <p>LGPD: nao loga nome nem contato do cliente — apenas identificadores.
 */
@Service
public class NotificacaoService {

  private static final Logger LOG = LoggerFactory.getLogger(NotificacaoService.class);

  public void registrarCriacaoAgendamento(
      UUID tenantId, UUID appointmentId, UUID clientId, String tipo) {
    LOG.warn(CorrelatedLogging.context(
        "Notificacao interna NAO registrada (modulo notifications pendente de migracao)",
        "tenantId", tenantId,
        "appointmentId", appointmentId,
        "clientId", clientId,
        "tipo", tipo));
  }
}
