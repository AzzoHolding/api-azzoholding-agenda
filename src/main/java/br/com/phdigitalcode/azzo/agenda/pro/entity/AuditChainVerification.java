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
 * Resultado de uma conferencia da trilha de auditoria de um salao (V164). Append-only: a
 * conferencia de ontem continua provando o que estava integro ontem.
 */
@Entity
@Table(name = "audit_chain_verifications")
@Getter
@Setter
public class AuditChainVerification {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  /** INTEGRA, INTEGRA_COM_RESSALVAS ou VIOLADA. */
  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "checked_events", nullable = false)
  private long checkedEvents;

  @Column(name = "sealed_events", nullable = false)
  private long sealedEvents;

  @Column(name = "legacy_events", nullable = false)
  private long legacyEvents;

  @Column(name = "legacy_forks", nullable = false)
  private long legacyForks;

  @Column(name = "redacted_events", nullable = false)
  private long redactedEvents;

  @Column(name = "pending_seal", nullable = false)
  private long pendingSeal;

  @Column(name = "failures", nullable = false)
  private long failures;

  @Column(name = "first_failure_event_id")
  private UUID firstFailureEventId;

  @Column(name = "first_failure_reason")
  private String firstFailureReason;

  @Column(name = "last_chain_seq")
  private Long lastChainSeq;

  @Column(name = "last_chain_hash")
  private String lastChainHash;

  @Column(name = "executed_by", nullable = false)
  private String executedBy;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = false)
  private Instant finishedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }
}
