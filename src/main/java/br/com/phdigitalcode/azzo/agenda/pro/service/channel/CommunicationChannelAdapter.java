package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/** Espelha {@code modules/chat/application/channel/CommunicationChannelAdapter.java}. */
public interface CommunicationChannelAdapter {

  ChatChannel channel();

  ChannelSendResult sendText(ChannelSendCommand command);
}
