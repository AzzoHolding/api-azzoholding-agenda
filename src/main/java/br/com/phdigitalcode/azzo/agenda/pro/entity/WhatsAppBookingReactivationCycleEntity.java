package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStage;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationStatus;

import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/chat/domain/entity/WhatsAppBookingReactivationCycleEntity.java}. */
@Entity
@Table(name = "whatsapp_booking_reactivation_cycles")
@Getter
@Setter
public class WhatsAppBookingReactivationCycleEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id")
  private UUID clientId;

  @Column(name = "conversation_id")
  private UUID conversationId;

  @Column(name = "user_identifier", nullable = false, length = 60)
  private String userIdentifier;

  @Column(name = "customer_name", length = 160)
  private String customerName;

  @Column(name = "origin_session_key", length = 120)
  private String originSessionKey;

  @Column(name = "abandoned_at", nullable = false)
  private Instant abandonedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_stage", nullable = false, length = 40)
  private WhatsAppBookingReactivationStage lastStage;

  @Column(name = "last_service_id")
  private UUID lastServiceId;

  @Column(name = "last_service_name", length = 160)
  private String lastServiceName;

  @Column(name = "last_professional_id")
  private UUID lastProfessionalId;

  @Column(name = "last_professional_name", length = 160)
  private String lastProfessionalName;

  @Column(name = "last_requested_date")
  private LocalDate lastRequestedDate;

  @Column(name = "last_requested_time", length = 10)
  private String lastRequestedTime;

  @Column(name = "assistant_last_prompt", length = 500)
  private String assistantLastPrompt;

  @Column(name = "customer_last_message", length = 500)
  private String customerLastMessage;

  @Column(name = "conversation_context_json", columnDefinition = "TEXT")
  private String conversationContextJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private WhatsAppBookingReactivationStatus status = WhatsAppBookingReactivationStatus.ACTIVE;

  @Column(name = "next_attempt_number", nullable = false)
  private int nextAttemptNumber = 1;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "reactivated_at")
  private Instant reactivatedAt;

  @Column(name = "converted_at")
  private Instant convertedAt;

  @Column(name = "appointment_id_created_after_abandonment")
  private UUID appointmentIdCreatedAfterAbandonment;

  @Column(name = "cancel_reason", length = 60)
  private String cancelReason;

  @Column(name = "responded_at")
  private Instant respondedAt;

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
    if (status == null) status = WhatsAppBookingReactivationStatus.ACTIVE;
    if (nextAttemptNumber <= 0) nextAttemptNumber = 1;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
