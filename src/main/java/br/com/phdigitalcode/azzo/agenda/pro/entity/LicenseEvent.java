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

/** Espelha {@code modules/billing/domain/entity/LicenseEvent.java}. Tabela {@code license_events}. */
@Entity
@Table(name = "license_events")
@Getter
@Setter
public class LicenseEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  /**
   * TRIAL_ACTIVATED, PLAN_ACTIVATED, PLAN_CANCELLED, PLAN_EXPIRED, TRIAL_EXTENDED,
   * LICENSE_RELEASED.
   */
  @Column(name = "event_type", nullable = false, length = 60)
  private String eventType;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Column(name = "metadata", columnDefinition = "TEXT")
  private String metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  public static LicenseEvent of(
      UUID tenantId, String eventType, UUID actorUserId, UUID productId, Instant validUntil) {
    LicenseEvent e = new LicenseEvent();
    e.tenantId = tenantId;
    e.eventType = eventType;
    e.actorUserId = actorUserId;
    e.productId = productId;
    e.validUntil = validUntil;
    return e;
  }
}
