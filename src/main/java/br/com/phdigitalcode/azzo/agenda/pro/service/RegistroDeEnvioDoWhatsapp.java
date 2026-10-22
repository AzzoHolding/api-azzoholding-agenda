package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.WhatsAppMessageLogEntity;
import br.com.phdigitalcode.azzo.agenda.pro.repository.WhatsAppMessageLogRepository;
import br.com.phdigitalcode.azzo.agenda.pro.service.channel.ChannelSendResult;

/**
 * Grava em "Mensagens enviadas" ({@code whatsapp_message_log}) cada aviso automatico ao cliente.
 *
 * <p>Confirmacao, cancelamento e lembrete saiam sem linha nenhuma no log: o webhook de status da
 * Meta chegava e respondia "sem mensagem correspondente", e a entrega deixava de ser rastreada
 * (teste ponta a ponta de 21/10/2026). E o {@code wamid} guardado aqui que liga o status a mensagem
 * ({@link ServicoStatusDeEntrega}).
 *
 * <p>Transacao propria: quem envia roda em transacao somente leitura (ou depois do commit), onde a
 * gravacao nao iria para o banco. E nunca lanca — o registro e consequencia do envio, nao o
 * objetivo.
 */
@Service
public class RegistroDeEnvioDoWhatsapp {

  public static final String CONFIRMACAO = "APPOINTMENT_CONFIRMATION";
  public static final String CANCELAMENTO = "APPOINTMENT_CANCELLATION";
  public static final String LEMBRETE = "APPOINTMENT_REMINDER";

  private static final Logger LOG = LoggerFactory.getLogger(RegistroDeEnvioDoWhatsapp.class);

  private final WhatsAppMessageLogRepository messageLogRepository;

  public RegistroDeEnvioDoWhatsapp(WhatsAppMessageLogRepository messageLogRepository) {
    this.messageLogRepository = messageLogRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void registrar(
      UUID tenantId,
      UUID appointmentId,
      String eventType,
      String destino,
      String texto,
      ChannelSendResult resultado) {
    if (tenantId == null || resultado == null) return;
    try {
      WhatsAppMessageLogEntity entrada = new WhatsAppMessageLogEntity();
      entrada.setTenantId(tenantId);
      entrada.setAppointmentId(appointmentId);
      entrada.setEventType(eventType);
      entrada.setDestinationPhone(destino == null || destino.isBlank() ? "-" : destino);
      entrada.setMessageText(texto);
      entrada.setProviderMessageId(resultado.providerMessageId());
      entrada.setStatus(resultado.success() ? ServicoStatusDeEntrega.ENVIADA : ServicoStatusDeEntrega.FALHOU);
      entrada.setErrorMessage(resultado.success() ? null : motivo(resultado));
      messageLogRepository.save(entrada);
    } catch (RuntimeException falha) {
      LOG.warn(
          "whatsapp.log.naoRegistrado tenantId={} appointmentId={} evento={} motivo={}",
          tenantId, appointmentId, eventType, falha.getMessage());
    }
  }

  private static String motivo(ChannelSendResult resultado) {
    String codigo = resultado.providerErrorCode();
    String mensagem = resultado.providerErrorMessage();
    if (codigo == null || codigo.isBlank()) return mensagem;
    return mensagem == null || mensagem.isBlank() ? codigo : codigo + ": " + mensagem;
  }
}
