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

/**
 * Espelha {@code modules/membership/domain/entity/ClientMembership.java}. Tabela
 * {@code client_memberships}.
 */
@Entity
@Table(name = "client_memberships")
@Getter
@Setter
public class ClientMembership {

  public static final String STATUS_ATIVA = "ATIVA";
  public static final String STATUS_INADIMPLENTE = "INADIMPLENTE";
  public static final String STATUS_SUSPENSA = "SUSPENSA";
  public static final String STATUS_CANCELADA = "CANCELADA";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  /** Nulo se o plano de origem foi excluido do catalogo depois da assinatura. */
  @Column(name = "plan_id")
  private UUID planId;

  @Column(name = "plan_nome", nullable = false)
  private String planNome;

  @Column(name = "preco_mensal", nullable = false)
  private BigDecimal precoMensal;

  @Column(name = "cumulativo", nullable = false)
  private boolean cumulativo = false;

  @Column(name = "asaas_subscription_id")
  private String asaasSubscriptionId;

  @Column(name = "status", nullable = false)
  private String status = STATUS_ATIVA;

  @Column(name = "period_start", nullable = false)
  private Instant periodStart;

  @Column(name = "period_end", nullable = false)
  private Instant periodEnd;

  @Column(name = "cancel_at_period_end", nullable = false)
  private boolean cancelAtPeriodEnd = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (id == null) id = UUID.randomUUID();
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
    if (!(o instanceof ClientMembership other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
