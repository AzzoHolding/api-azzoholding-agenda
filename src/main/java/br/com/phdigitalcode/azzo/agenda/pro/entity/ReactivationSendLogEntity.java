package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/chat/domain/entity/ReactivationSendLogEntity.java}. */
@Entity
@Table(name = "reactivation_send_log")
@Getter
@Setter
public class ReactivationSendLogEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "cycle_id", nullable = false)
  private UUID cycleId;

  @Column(name = "attempt_number", nullable = false)
  private int attemptNumber;

  @Column(name = "status", nullable = false)
  private String status = "SENT";

  @Column(name = "provider_message_id")
  private String providerMessageId;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (sentAt == null) sentAt = Instant.now();
    if (expiresAt == null) expiresAt = sentAt.plusSeconds(365L * 24L * 60L * 60L);
  }
}
