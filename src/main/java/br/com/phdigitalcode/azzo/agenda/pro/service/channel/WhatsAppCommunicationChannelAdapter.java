package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantWhatsAppConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.integration.WhatsAppClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantWhatsAppConfigRepository;

/** Espelha {@code modules/chat/application/channel/WhatsAppCommunicationChannelAdapter.java}. */
@Service
public class WhatsAppCommunicationChannelAdapter implements CommunicationChannelAdapter {

  private final TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository;
  private final WhatsAppClient whatsAppClient;

  public WhatsAppCommunicationChannelAdapter(
      TenantWhatsAppConfigRepository tenantWhatsAppConfigRepository, WhatsAppClient whatsAppClient) {
    this.tenantWhatsAppConfigRepository = tenantWhatsAppConfigRepository;
    this.whatsAppClient = whatsAppClient;
  }

  @Override
  public ChatChannel channel() {
    return ChatChannel.WHATSAPP;
  }

  @Override
  public ChannelSendResult sendText(ChannelSendCommand command) {
    TenantWhatsAppConfig config = tenantWhatsAppConfigRepository.findById(command.tenantId()).orElse(null);
    if (config == null || !config.isWhatsappEnabled()) {
      return ChannelSendResult.failed(
          "WHATSAPP_NOT_ENABLED",
          "WhatsApp nao habilitado para o tenant.");
    }

    try {
      String providerMessageId = whatsAppClient.sendMessage(
          config,
          command.recipientExternalId(),
          command.content());
      return ChannelSendResult.sent(providerMessageId);
    } catch (RuntimeException ex) {
      return ChannelSendResult.failed("WHATSAPP_SEND_ERROR", safeError(ex.getMessage()));
    }
  }

  private String safeError(String value) {
    if (value == null || value.isBlank()) return "Falha ao enviar mensagem no WhatsApp";
    return value.length() > 500 ? value.substring(0, 500) : value;
  }
}
