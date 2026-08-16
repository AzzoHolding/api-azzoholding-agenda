package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.MetodoPagamento;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.TipoTransacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/finance/domain/entity/RecurringTransaction.java}. Tabela {@code recurring_transactions}. */
@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
public class RecurringTransaction {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private TipoTransacao type;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false)
  private MetodoPagamento paymentMethod;

  /** MONTHLY ou WEEKLY */
  @Column(name = "frequency", nullable = false)
  private String frequency;

  /** Dia do mes para MONTHLY (1-28) */
  @Column(name = "day_of_month")
  private Short dayOfMonth;

  /** Dia da semana para WEEKLY (0=dom ... 6=sab) */
  @Column(name = "day_of_week")
  private Short dayOfWeek;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RecurringTransaction other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
