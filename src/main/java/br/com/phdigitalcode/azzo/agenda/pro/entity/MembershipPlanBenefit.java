package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/membership/domain/entity/MembershipPlanBenefit.java}. Tabela
 * {@code membership_plan_benefits}.
 */
@Entity
@Table(name = "membership_plan_benefits")
@Getter
@Setter
public class MembershipPlanBenefit {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "quantidade_mensal", nullable = false)
  private int quantidadeMensal;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MembershipPlanBenefit other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
