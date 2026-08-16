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
 * Espelha {@code modules/chat/domain/entity/ReactivationConsentHistoryEntity.java}. Histórico de
 * consentimento de reativação (append-only — NUNCA deletar registros desta tabela).
 */
@Entity
@Table(name = "reactivation_consent_history")
@Getter
@Setter
public class ReactivationConsentHistoryEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  /** OPT_IN ou OPT_OUT */
  @Column(name = "action", nullable = false, length = 10)
  private String action;

  /** WHATSAPP_REPLY | OWNER | SYSTEM | PORTAL */
  @Column(name = "source", nullable = false, length = 30)
  private String source;

  /** Nulo quando source = SYSTEM ou WHATSAPP_REPLY */
  @Column(name = "registered_by")
  private UUID registeredBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
