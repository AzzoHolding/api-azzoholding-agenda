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

/**
 * Espelha {@code domain/entity/WebhookEventLog.java}. Tabela {@code webhook_event_logs}.
 *
 * <p>{@code idempotencyKey} e unica — e o que garante que o mesmo evento reenviado pelo Asaas nao
 * seja processado duas vezes.
 */
@Entity
@Table(name = "webhook_event_logs")
@Getter
@Setter
public class WebhookEventLog {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "external_subscription_id")
  private String externalSubscriptionId;

  @Column(name = "external_payment_id")
  private String externalPaymentId;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash;

  @Column(name = "payload_json", nullable = false)
  private String payloadJson;

  @Column(name = "processed", nullable = false)
  private boolean processed;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
