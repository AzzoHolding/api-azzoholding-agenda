package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.converter.StatusSubscriptionConverter;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusSubscription;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/billing/domain/entity/Subscription.java}. Tabela {@code subscriptions}.
 *
 * <p>Os {@code @ManyToOne} de conveniencia do original ({@code tenant}, {@code product}) nao foram
 * mapeados — mesma decisao das etapas anteriores: todo o codigo resolve o produto pelo
 * {@code productId} via repositorio, e a associacao so traria N+1/lazy.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
public class Subscription {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "asaas_subscription_id", nullable = false, unique = true)
  private String asaasSubscriptionId;

  @Column(name = "asaas_customer_id", nullable = false)
  private String asaasCustomerId;

  @Column(name = "plan_code")
  private String planCode;

  @Column(name = "billing_type", nullable = false)
  private String billingType;

  @Convert(converter = StatusSubscriptionConverter.class)
  @Column(name = "status", nullable = false)
  private StatusSubscription status;

  @Column(name = "value_cents", nullable = false)
  private long valueCents;

  @Column(name = "cycle", nullable = false)
  private String cycle = "MONTHLY";

  @Column(name = "next_due_date")
  private LocalDate nextDueDate;

  @Column(name = "payment_link")
  private String paymentLink;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
