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

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatAppointmentMarker;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.ChatChannel;

import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/chat/domain/entity/ChatConversationEntity.java}. */
@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
public class ChatConversationEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private ChatChannel channel = ChatChannel.WHATSAPP;

  @Column(name = "external_contact_id")
  private String externalContactId;

  @Enumerated(EnumType.STRING)
  @Column(name = "appointment_marker", nullable = false)
  private ChatAppointmentMarker appointmentMarker = ChatAppointmentMarker.NAO_INICIADO;

  @Column(name = "last_message_at")
  private Instant lastMessageAt;

  @Column(name = "last_message_preview")
  private String lastMessagePreview;

  @Column(name = "manual_mode_until")
  private Instant manualModeUntil;

  @Column(name = "manual_mode_by_user_id")
  private UUID manualModeByUserId;

  @Column(name = "manual_mode_reason")
  private String manualModeReason;

  @Column(name = "unread_count", nullable = false)
  private int unreadCount = 0;

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
    if (channel == null) channel = ChatChannel.WHATSAPP;
    if (appointmentMarker == null) appointmentMarker = ChatAppointmentMarker.NAO_INICIADO;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
