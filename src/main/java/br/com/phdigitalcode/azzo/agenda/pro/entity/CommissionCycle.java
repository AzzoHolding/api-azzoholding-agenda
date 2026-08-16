package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/commission/domain/entity/CommissionCycle.java}. Tabela {@code commission_cycles}. */
@Entity
@Table(name = "commission_cycles")
@Getter
@Setter
public class CommissionCycle {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  /** CLOSED | PAID */
  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by_user_id")
  private UUID closedByUserId;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "paid_by_user_id")
  private UUID paidByUserId;

  @Column(name = "total_amount_cents", nullable = false)
  private long totalAmountCents;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CommissionCycle other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
