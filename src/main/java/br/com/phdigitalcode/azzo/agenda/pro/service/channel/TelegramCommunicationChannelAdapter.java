package br.com.phdigitalcode.azzo.agenda.pro.service.channel;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.TenantTelegramConfig;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.integration.TelegramBotClient;
import br.com.phdigitalcode.azzo.agenda.pro.repository.TenantTelegramConfigRepository;

/** Espelha {@code modules/chat/application/channel/TelegramCommunicationChannelAdapter.java}. */
@Service
public class TelegramCommunicationChannelAdapter implements CommunicationChannelAdapter {

  private final TenantTelegramConfigRepository tenantTelegramConfigRepository;
  private final TelegramBotClient telegramBotClient;

  public TelegramCommunicationChannelAdapter(
      TenantTelegramConfigRepository tenantTelegramConfigRepository, TelegramBotClient telegramBotClient) {
    this.tenantTelegramConfigRepository = tenantTelegramConfigRepository;
    this.telegramBotClient = telegramBotClient;
  }

  @Override
  public ChatChannel channel() {
    return ChatChannel.TELEGRAM;
  }

  @Override
  public ChannelSendResult sendText(ChannelSendCommand command) {
    TenantTelegramConfig config = tenantTelegramConfigRepository.findById(command.tenantId()).orElse(null);
    if (config == null || !config.isTelegramEnabled()) {
      return ChannelSendResult.failed(
          "TELEGRAM_NOT_ENABLED",
          "Telegram nao habilitado para o tenant.");
    }

    try {
      String providerMessageId = telegramBotClient.sendMessage(
          config,
          command.recipientExternalId(),
          command.content());
      return ChannelSendResult.sent(providerMessageId);
    } catch (RuntimeException ex) {
      return ChannelSendResult.failed("TELEGRAM_SEND_ERROR", safeError(ex.getMessage()));
    }
  }

  private String safeError(String value) {
    if (value == null || value.isBlank()) return "Falha ao enviar mensagem no Telegram";
    return value.length() > 500 ? value.substring(0, 500) : value;
  }
}
