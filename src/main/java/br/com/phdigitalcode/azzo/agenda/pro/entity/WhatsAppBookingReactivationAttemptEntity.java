package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.WhatsAppBookingReactivationAttemptStatus;

import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/chat/domain/entity/WhatsAppBookingReactivationAttemptEntity.java}. */
@Entity
@Table(name = "whatsapp_booking_reactivation_attempts")
@Getter
@Setter
public class WhatsAppBookingReactivationAttemptEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "cycle_id", nullable = false)
  private UUID cycleId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Column(name = "scheduled_for", nullable = false)
  private Instant scheduledFor;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private WhatsAppBookingReactivationAttemptStatus status =
      WhatsAppBookingReactivationAttemptStatus.PENDING;

  @Column(name = "provider_message_id", length = 120)
  private String providerMessageId;

  @Column(name = "error_message", length = 255)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (status == null) status = WhatsAppBookingReactivationAttemptStatus.PENDING;
  }
}
