package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/commission/domain/entity/CommissionRule.java}. Tabela {@code commission_rules}. */
@Entity
@Table(name = "commission_rules")
@Getter
@Setter
public class CommissionRule {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "rule_set_id", nullable = false)
  private UUID ruleSetId;

  /** GENERAL | SERVICE | SERVICE_CATEGORY | PRODUCT | PRODUCT_CATEGORY */
  @Column(name = "target_type", nullable = false)
  private String targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(name = "target_code")
  private String targetCode;

  @Column(name = "percent_value", nullable = false)
  private BigDecimal percentValue;

  @Column(name = "fixed_amount_cents", nullable = false)
  private long fixedAmountCents;

  /** GROSS | NET_OF_DISCOUNT */
  @Column(name = "percent_base_type", nullable = false)
  private String percentBaseType;

  /** KEEP_COMMISSION | REVERSE_COMMISSION */
  @Column(name = "refund_policy", nullable = false)
  private String refundPolicy;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

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
    if (!(o instanceof CommissionRule other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
