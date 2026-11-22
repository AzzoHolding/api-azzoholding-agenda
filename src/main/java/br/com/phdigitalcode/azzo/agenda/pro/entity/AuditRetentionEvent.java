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
 * Espelha {@code modules/audit/domain/entity/AuditRetentionEvent.java}. Tabela
 * {@code audit_retention_events} — evidencia de cada expurgo executado (LGPD art. 16, prazo de
 * retencao).
 */
@Entity
@Table(name = "audit_retention_events")
@Getter
@Setter
public class AuditRetentionEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "policy_version", nullable = false)
  private String policyVersion;

  @Column(name = "retention_period_days", nullable = false)
  private int retentionPeriodDays;

  @Column(name = "window_start", nullable = false)
  private Instant windowStart;

  @Column(name = "window_end", nullable = false)
  private Instant windowEnd;

  @Column(name = "affected_rows", nullable = false)
  private long affectedRows;

  @Column(name = "executed_by", nullable = false)
  private String executedBy;

  @Column(name = "execution_id", nullable = false)
  private String executionId;

  @Column(name = "evidence_hash", nullable = false)
  private String evidenceHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /**
   * Onde a cadeia continua depois do expurgo: o elo do ultimo evento removido ({@code chain_hash}
   * na v2, {@code event_hash} no legado) e a posicao dele (so v2). O primeiro evento que sobra aponta
   * para este hash.
   */
  @Column(name = "chain_anchor_hash")
  private String chainAnchorHash;

  @Column(name = "chain_anchor_seq")
  private Long chainAnchorSeq;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AuditRetentionEvent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
