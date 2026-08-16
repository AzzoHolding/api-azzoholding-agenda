package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

/** Espelha {@code CommunicationChannelDispatcherUnitTest} do original. */
class CommunicationChannelDispatcherTest {

  @Test
  void deveEnviarPeloAdapterDoCanalSolicitado() {
    CapturingAdapter whatsApp = new CapturingAdapter(ChatChannel.WHATSAPP, ChannelSendResult.sent("wamid-1"));
    CommunicationChannelDispatcher dispatcher = new CommunicationChannelDispatcher(List.of(whatsApp));

    ChannelSendCommand command =
        new ChannelSendCommand(UUID.randomUUID(), ChatChannel.WHATSAPP, "5511999991111", "Ola");

    ChannelSendResult result = dispatcher.sendText(command);

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).isEqualTo("wamid-1");
    assertThat(whatsApp.lastCommand).isEqualTo(command);
  }

  @Test
  void deveFalharQuandoCanalNaoTiverAdapter() {
    CommunicationChannelDispatcher dispatcher = new CommunicationChannelDispatcher(
        List.of(new CapturingAdapter(ChatChannel.WHATSAPP, ChannelSendResult.sent("wamid-1"))));

    ChannelSendResult result = dispatcher.sendText(
        new ChannelSendCommand(UUID.randomUUID(), ChatChannel.TELEGRAM, "123456", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("CHANNEL_NOT_SUPPORTED");
  }

  @Test
  void deveFalharQuandoNaoHouverAdaptersRegistrados() {
    CommunicationChannelDispatcher dispatcher = new CommunicationChannelDispatcher(null);

    ChannelSendResult result = dispatcher.sendText(
        new ChannelSendCommand(UUID.randomUUID(), ChatChannel.WHATSAPP, "123456", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("CHANNEL_NOT_SUPPORTED");
  }

  private static final class CapturingAdapter implements CommunicationChannelAdapter {
    private final ChatChannel channel;
    private final ChannelSendResult result;
    private ChannelSendCommand lastCommand;

    private CapturingAdapter(ChatChannel channel, ChannelSendResult result) {
      this.channel = channel;
      this.result = result;
    }

    @Override
    public ChatChannel channel() {
      return channel;
    }

    @Override
    public ChannelSendResult sendText(ChannelSendCommand command) {
      lastCommand = command;
      return result;
    }
  }
}
