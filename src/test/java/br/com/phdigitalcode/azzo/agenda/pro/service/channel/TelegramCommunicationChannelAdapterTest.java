package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TelegramBotClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;

class TelegramCommunicationChannelAdapterTest {

  private TenantTelegramConfigRepository configRepository;
  private TelegramBotClient telegramBotClient;
  private TelegramCommunicationChannelAdapter adapter;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    configRepository = mock(TenantTelegramConfigRepository.class);
    telegramBotClient = mock(TelegramBotClient.class);
    adapter = new TelegramCommunicationChannelAdapter(configRepository, telegramBotClient);
  }

  @Test
  void channelRetornaTelegram() {
    assertThat(adapter.channel()).isEqualTo(ChatChannel.TELEGRAM);
  }

  @Test
  void falhaQuandoConfigAusente() {
    when(configRepository.findById(tenantId)).thenReturn(Optional.empty());

    ChannelSendResult result =
        adapter.sendText(new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, "123456789", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("TELEGRAM_NOT_ENABLED");
  }

  @Test
  void falhaQuandoTelegramDesabilitado() {
    TenantTelegramConfig config = new TenantTelegramConfig();
    config.setTenantId(tenantId);
    config.setTelegramEnabled(false);
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config));

    ChannelSendResult result =
        adapter.sendText(new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, "123456789", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("TELEGRAM_NOT_ENABLED");
  }

  @Test
  void enviaComSucessoQuandoConfigHabilitada() {
    TenantTelegramConfig config = new TenantTelegramConfig();
    config.setTenantId(tenantId);
    config.setTelegramEnabled(true);
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config));
    when(telegramBotClient.sendMessage(eq(config), eq("123456789"), eq("Ola"))).thenReturn("tg-1");

    ChannelSendResult result =
        adapter.sendText(new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, "123456789", "Ola"));

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).isEqualTo("tg-1");
  }

  @Test
  void mapeiaExcecaoDoClienteParaFalha() {
    TenantTelegramConfig config = new TenantTelegramConfig();
    config.setTenantId(tenantId);
    config.setTelegramEnabled(true);
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config));
    when(telegramBotClient.sendMessage(any(TenantTelegramConfig.class), any(), any()))
        .thenThrow(new IllegalStateException("boom"));

    ChannelSendResult result =
        adapter.sendText(new ChannelSendCommand(tenantId, ChatChannel.TELEGRAM, "123456789", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("TELEGRAM_SEND_ERROR");
    assertThat(result.providerErrorMessage()).isEqualTo("boom");
  }
}
