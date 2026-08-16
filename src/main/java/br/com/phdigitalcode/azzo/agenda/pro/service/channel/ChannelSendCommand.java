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
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatorio para envio.");
    if (channel == null) throw new IllegalArgumentException("Canal obrigatorio para envio.");
    if (recipientExternalId == null || recipientExternalId.isBlank()) {
      throw new IllegalArgumentException("Destino obrigatorio para envio.");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Conteudo obrigatorio para envio.");
    }
  }
}
