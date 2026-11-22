package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatConversationEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.Cliente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatConversationRepository;

/** Espelha {@code modules/chat/application/CustomerCommunicationChannelResolver.java}. */
@Service
public class CustomerCommunicationChannelResolver {

  private final ChatConversationRepository chatConversationRepository;

  public CustomerCommunicationChannelResolver(ChatConversationRepository chatConversationRepository) {
    this.chatConversationRepository = chatConversationRepository;
  }

  public ResolvedChannel resolve(UUID tenantId, Cliente client, String... whatsAppFallbacks) {
    return resolve(tenantId, client, null, whatsAppFallbacks);
  }

  public ResolvedChannel resolve(
      UUID tenantId,
      Cliente client,
      ChatConversationEntity preferredConversation,
      String... whatsAppFallbacks) {
    if (tenantId == null || client == null || client.getId() == null) {
      throw new IllegalArgumentException("Cliente inválido para roteamento de canal.");
    }

    ChatConversationEntity conversation = preferredConversation != null
        ? preferredConversation
        : chatConversationRepository
            .findLatestByTenantAndClient(tenantId, client.getId())
            .orElse(null);
    if (conversation != null && conversation.getChannel() == ChatChannel.AREA_CLIENTE) {
      // Resposta na conversa aberta da area: fica na area. Sem conversa escolhida (lembretes, confirmacoes, reativacao),
      // a conversa da area nao serve de rota: vale a do WhatsApp ou a do Telegram, ou o telefone.
      if (preferredConversation != null) {
        return new ResolvedChannel(ChatChannel.AREA_CLIENTE, normalize(conversation.getExternalContactId()), conversation);
      }
      conversation = chatConversationRepository.findByTenantClientAndChannel(tenantId, client.getId(), ChatChannel.WHATSAPP)
          .or(() -> chatConversationRepository.findByTenantClientAndChannel(tenantId, client.getId(), ChatChannel.TELEGRAM))
          .orElse(null);
    }

    if (conversation != null && conversation.getChannel() == ChatChannel.TELEGRAM) {
      String telegramExternalId = normalize(conversation.getExternalContactId());
      if (telegramExternalId == null) {
        throw new IllegalArgumentException("Conversa Telegram sem destino configurado.");
      }
      return new ResolvedChannel(ChatChannel.TELEGRAM, telegramExternalId, conversation);
    }

    String whatsAppExternalId = firstNonBlank(
        conversation != null ? conversation.getExternalContactId() : null,
        whatsAppFallbacks);
    return new ResolvedChannel(ChatChannel.WHATSAPP, whatsAppExternalId, conversation);
  }

  private String firstNonBlank(String firstValue, String... remainingValues) {
    String normalizedFirst = normalize(firstValue);
    if (normalizedFirst != null) return normalizedFirst;
    if (remainingValues == null) return null;
    for (String value : remainingValues) {
      String normalized = normalize(value);
      if (normalized != null) return normalized;
    }
    return null;
  }

  private String normalize(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  public record ResolvedChannel(
      ChatChannel channel,
      String externalContactId,
      ChatConversationEntity conversation) {}
}
