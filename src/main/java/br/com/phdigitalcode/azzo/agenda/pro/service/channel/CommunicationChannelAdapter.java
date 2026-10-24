package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/** Espelha {@code modules/chat/application/channel/CommunicationChannelAdapter.java}. */
public interface CommunicationChannelAdapter {

  ChatChannel channel();

  ChannelSendResult sendText(ChannelSendCommand command);

  /**
   * Manda um template aprovado.
   *
   * <p>O padrao manda o texto equivalente: canal sem conceito de template (Telegram) nao deve
   * quebrar por causa de uma exigencia do WhatsApp.
   */
  default ChannelSendResult sendTemplate(ChannelTemplateCommand command) {
    return sendText(
        new ChannelSendCommand(
            command.tenantId(),
            command.channel(),
            command.recipientExternalId(),
            command.textoEquivalente()));
  }
}
