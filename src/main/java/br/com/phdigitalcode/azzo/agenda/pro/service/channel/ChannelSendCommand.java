package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/** Espelha {@code modules/chat/application/channel/ChannelSendCommand.java}. */
public record ChannelSendCommand(
    UUID tenantId,
    ChatChannel channel,
    String recipientExternalId,
    String content) {

  public ChannelSendCommand {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatório para envio.");
    if (channel == null) throw new IllegalArgumentException("Canal obrigatório para envio.");
    if (recipientExternalId == null || recipientExternalId.isBlank()) {
      throw new IllegalArgumentException("Destino obrigatório para envio.");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Conteúdo obrigatório para envio.");
    }
  }
}
