package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/commission/domain/entity/CommissionEntry.java}. Tabela {@code commission_entries}. */
@Entity
@Table(name = "commission_entries")
@Getter
@Setter
public class CommissionEntry {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "professional_id", nullable = false)
  private UUID professionalId;

  @Column(name = "cycle_id")
  private UUID cycleId;

  /** SERVICE | PRODUCT | MANUAL_ADJUSTMENT */
  @Column(name = "origin_type", nullable = false)
  private String originType;

  @Column(name = "origin_id")
  private UUID originId;

  @Column(name = "origin_reference")
  private String originReference;

  @Column(name = "rule_set_id")
  private UUID ruleSetId;

  @Column(name = "rule_id")
  private UUID ruleId;

  @Column(name = "period_key", nullable = false)
  private String periodKey;

  @Column(name = "base_amount_cents", nullable = false)
  private long baseAmountCents;

  @Column(name = "percent_value", nullable = false)
  private BigDecimal percentValue;

  @Column(name = "percent_amount_cents", nullable = false)
  private long percentAmountCents;

  @Column(name = "fixed_amount_cents", nullable = false)
  private long fixedAmountCents;

  @Column(name = "total_amount_cents", nullable = false)
  private long totalAmountCents;

  /** OPEN | PAID | REVERSED */
  @Column(name = "entry_status", nullable = false)
  private String entryStatus;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "reversed_at")
  private Instant reversedAt;

  @Column(name = "reversal_entry_id")
  private UUID reversalEntryId;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CommissionEntry other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
