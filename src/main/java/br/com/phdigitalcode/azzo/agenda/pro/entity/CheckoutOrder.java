package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.converter.StatusCheckoutConverter;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusCheckout;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/billing/domain/entity/CheckoutOrder.java}.
 *
 * <p><b>Armadilha</b>: a tabela e {@code orders}, nao {@code checkout_orders}. E
 * {@code orders.status} guarda a descricao em portugues ({@code 'Confirmado'}), nao
 * {@code 'CONFIRMED'} — ver {@code PlanLimitsRepository}, que consulta essa tabela em SQL nativo.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
public class CheckoutOrder {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "intent_id", nullable = false, unique = true)
  private UUID intentId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "total", nullable = false)
  private long total;

  @Convert(converter = StatusCheckoutConverter.class)
  @Column(name = "status", nullable = false)
  private StatusCheckout status;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
