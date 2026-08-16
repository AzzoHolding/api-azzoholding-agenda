package br.com.phdigitalcode.azzo.agenda.pro.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.phdigitalcode.azzo.agenda.pro.entity.ChatMessageEntity;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;
import br.com.phdigitalcode.azzo.agenda.pro.repository.ChatMessageRepository;

/** Espelha {@code modules/chat/application/ChatMessageStatusService.java}. */
@Service
public class ChatMessageStatusService {

  private final ChatMessageRepository chatMessageRepository;

  public ChatMessageStatusService(ChatMessageRepository chatMessageRepository) {
    this.chatMessageRepository = chatMessageRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean applyProviderStatus(
      UUID tenantId,
      String providerMessageId,
      String providerStatus,
      String providerErrorCode,
      String providerErrorMessage,
      Instant occurredAt) {
    if (tenantId == null || providerMessageId == null || providerMessageId.isBlank()) return false;
    ChatMessageEntity message =
        chatMessageRepository
            .findByTenantIdAndProviderMessageId(tenantId, providerMessageId)
            .orElse(null);
    if (message == null) return false;

    ChatMessageStatus status = mapProviderStatus(providerStatus);
    if (status == null) return false;
    Instant when = occurredAt == null ? Instant.now() : occurredAt;

    message.setStatus(status);
    message.setProviderErrorCode(normalize(providerErrorCode));
    message.setProviderErrorMessage(normalize(providerErrorMessage));

    switch (status) {
      case SENT -> message.setSentAt(when);
      case DELIVERED -> message.setDeliveredAt(when);
      case READ -> message.setReadAt(when);
      case FAILED -> message.setFailedAt(when);
      default -> {
      }
    }
    return true;
  }

  private ChatMessageStatus mapProviderStatus(String providerStatus) {
    if (providerStatus == null || providerStatus.isBlank()) return null;
    String value = providerStatus.trim().toLowerCase();
    return switch (value) {
      case "sent" -> ChatMessageStatus.SENT;
      case "delivered" -> ChatMessageStatus.DELIVERED;
      case "read" -> ChatMessageStatus.READ;
      case "failed" -> ChatMessageStatus.FAILED;
      default -> null;
    };
  }

  private String normalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
