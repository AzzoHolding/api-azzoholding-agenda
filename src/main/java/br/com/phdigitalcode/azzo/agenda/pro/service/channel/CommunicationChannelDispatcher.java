package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/**
 * Espelha {@code modules/chat/application/channel/CommunicationChannelDispatcher.java}. O
 * {@code @All List<CommunicationChannelAdapter>} do Quarkus (io.quarkus.arc.All) vira injecao de
 * construtor de {@code List<CommunicationChannelAdapter>} — o Spring resolve automaticamente
 * todos os beans que implementam a interface, mesma semantica.
 */
@Service
public class CommunicationChannelDispatcher {

  private final List<CommunicationChannelAdapter> adapters;

  public CommunicationChannelDispatcher(List<CommunicationChannelAdapter> adapters) {
    this.adapters = adapters;
  }

  public ChannelSendResult sendText(ChannelSendCommand command) {
    if (command == null) throw new IllegalArgumentException("Comando de envio obrigatorio.");
    return adapters().stream()
        .filter(adapter -> adapter.channel() == command.channel())
        .findFirst()
        .map(adapter -> adapter.sendText(command))
        .orElseGet(() -> unsupported(command.channel()));
  }

  List<CommunicationChannelAdapter> adapters() {
    return adapters == null ? List.of() : adapters;
  }

  private ChannelSendResult unsupported(ChatChannel channel) {
    return ChannelSendResult.failed(
        "CHANNEL_NOT_SUPPORTED",
        "Canal de comunicacao nao suportado: " + channel.name());
  }
}
