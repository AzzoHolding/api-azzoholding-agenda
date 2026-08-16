package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.converter.StatusCheckoutConverter;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/billing/domain/entity/CheckoutIntent.java}. Tabela {@code checkout_intents}. */
@Entity
@Table(name = "checkout_intents")
@Getter
@Setter
public class CheckoutIntent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "product_name_snapshot", nullable = false)
  private String productNameSnapshot;

  @Column(name = "currency_snapshot", nullable = false)
  private String currencySnapshot;

  @Column(name = "currency")
  private String currency;

  @Column(name = "unit_price_snapshot", nullable = false)
  private BigDecimal unitPriceSnapshot;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "total_price_snapshot", nullable = false)
  private BigDecimal totalPriceSnapshot;

  @Column(name = "calculated_total")
  private BigDecimal calculatedTotal;

  /** ATENCAO: a coluna guarda a descricao em portugues ({@code 'Pendente'}), nao o nome do enum. */
  @Convert(converter = StatusCheckoutConverter.class)
  @Column(name = "status", nullable = false)
  private StatusCheckout status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "payment_reference")
  private String paymentReference;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

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
}
