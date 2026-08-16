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

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;

class WhatsAppCommunicationChannelAdapterTest {

  private TenantWhatsAppConfigRepository configRepository;
  private WhatsAppClient whatsAppClient;
  private WhatsAppCommunicationChannelAdapter adapter;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    configRepository = mock(TenantWhatsAppConfigRepository.class);
    whatsAppClient = mock(WhatsAppClient.class);
    adapter = new WhatsAppCommunicationChannelAdapter(configRepository, whatsAppClient);
  }

  @Test
  void channelRetornaWhatsapp() {
    assertThat(adapter.channel()).isEqualTo(ChatChannel.WHATSAPP);
  }

  @Test
  void falhaQuandoConfigAusente() {
    when(configRepository.findById(tenantId)).thenReturn(Optional.empty());

    ChannelSendResult result = adapter.sendText(
        new ChannelSendCommand(tenantId, ChatChannel.WHATSAPP, "5511999991111", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("WHATSAPP_NOT_ENABLED");
  }

  @Test
  void falhaQuandoWhatsappDesabilitado() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappEnabled(false);
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config));

    ChannelSendResult result = adapter.sendText(
        new ChannelSendCommand(tenantId, ChatChannel.WHATSAPP, "5511999991111", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("WHATSAPP_NOT_ENABLED");
  }

  @Test
  void enviaComSucessoQuandoConfigHabilitada() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappEnabled(true);
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config));
    when(whatsAppClient.sendMessage(eq(config), eq("5511999991111"), eq("Ola"))).thenReturn("wamid-1");

    ChannelSendResult result = adapter.sendText(
        new ChannelSendCommand(tenantId, ChatChannel.WHATSAPP, "5511999991111", "Ola"));

    assertThat(result.success()).isTrue();
    assertThat(result.providerMessageId()).isEqualTo("wamid-1");
  }

  @Test
  void mapeiaExcecaoDoClienteParaFalha() {
    TenantWhatsAppConfig config = new TenantWhatsAppConfig();
    config.setTenantId(tenantId);
    config.setWhatsappEnabled(true);
    when(configRepository.findById(tenantId)).thenReturn(Optional.of(config));
    when(whatsAppClient.sendMessage(any(), any(), any()))
        .thenThrow(new IllegalStateException("Falha ao enviar mensagem no WhatsApp - Token nao autorizado"));

    ChannelSendResult result = adapter.sendText(
        new ChannelSendCommand(tenantId, ChatChannel.WHATSAPP, "5511999991111", "Ola"));

    assertThat(result.success()).isFalse();
    assertThat(result.providerErrorCode()).isEqualTo("WHATSAPP_SEND_ERROR");
    assertThat(result.providerErrorMessage()).contains("Token nao autorizado");
  }
}
