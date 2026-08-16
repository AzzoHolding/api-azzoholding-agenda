package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageDirection;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatMessageStatus;

import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/chat/domain/entity/ChatMessageEntity.java}. */
@Entity
@Table(name = "chat_messages")
@Getter
@Setter
public class ChatMessageEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false)
  private ChatMessageDirection direction;

  @Column(name = "content")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ChatMessageStatus status;

  @Column(name = "provider_message_id")
  private String providerMessageId;

  @Column(name = "provider_error_code")
  private String providerErrorCode;

  @Column(name = "provider_error_message")
  private String providerErrorMessage;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
