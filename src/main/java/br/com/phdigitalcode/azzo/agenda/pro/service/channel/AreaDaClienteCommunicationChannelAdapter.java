package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/**
 * Canal da area da cliente (V188). Nao ha provedor externo: a mensagem do salao ja fica gravada no chat e a cliente a le
 * na area. O adaptador so confirma o envio e avisa (evento) para o e-mail de "nova mensagem". O destino e o id da conta.
 *
 * <p>A resposta do assistente nao passa por aqui (vai direto para o registro), entao nao gera e-mail.
 */
@Component
public class AreaDaClienteCommunicationChannelAdapter implements CommunicationChannelAdapter {

  /** Mensagem de uma pessoa do salao para a cliente, pela area. */
  public record MensagemDoSalaoNaArea(UUID tenantId, String contaId) {}

  private final ApplicationEventPublisher eventos;

  public AreaDaClienteCommunicationChannelAdapter(ApplicationEventPublisher eventos) {
    this.eventos = eventos;
  }

  @Override
  public ChatChannel channel() {
    return ChatChannel.AREA_CLIENTE;
  }

  @Override
  public ChannelSendResult sendText(ChannelSendCommand command) {
    if (command.recipientExternalId() == null || command.recipientExternalId().isBlank()) {
      return ChannelSendResult.failed("AREA_CLIENTE_SEM_CONTA", "A cliente não tem conta ligada a este cadastro.");
    }
    try {
      eventos.publishEvent(new MensagemDoSalaoNaArea(command.tenantId(), command.recipientExternalId()));
    } catch (RuntimeException ignored) {
      // o aviso por e-mail nunca derruba o envio
    }
    return ChannelSendResult.sent("area-" + UUID.randomUUID());
  }
}
