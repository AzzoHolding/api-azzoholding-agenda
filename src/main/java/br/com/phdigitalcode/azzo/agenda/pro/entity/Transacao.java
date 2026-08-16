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
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/finance/domain/entity/Transacao.java}. Tabela {@code transactions}.
 *
 * <p>Das associacoes {@code @ManyToOne} do original, so {@code categoryRef} e
 * {@code productCategoryRef} foram mantidas — sao as unicas efetivamente navegadas
 * (queries por {@code categoryRef.name} no estorno de receita/comissao e na montagem do
 * resumo financeiro). {@code tenant}, {@code appointment}, {@code professional} e
 * {@code stockItem} viraram apenas colunas escalares: o codigo original nunca navega esses
 * grafos, e mante-los so traria N+1 e proxies desnecessarios.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
public class Transacao {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "professional_id")
  private UUID professionalId;

  @Column(name = "comanda_id")
  private UUID comandaId;

  @Column(name = "stock_item_id")
  private UUID stockItemId;

  @Column(name = "product_category_id")
  private UUID productCategoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_category_id", insertable = false, updatable = false)
  private ProductCategory productCategoryRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private TipoTransacao type;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id", insertable = false, updatable = false)
  private TransactionCategory categoryRef;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false)
  private MetodoPagamento paymentMethod;

  @Column(name = "date", nullable = false)
  private Instant date;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** MANUAL | RECURRING | APPOINTMENT */
  @Column(name = "source", nullable = false)
  private String source = "MANUAL";

  @Column(name = "recurring_id")
  private UUID recurringId;

  @Column(name = "reconciled", nullable = false)
  private boolean reconciled = false;

  @Column(name = "reconciled_at")
  private Instant reconciledAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (date == null) date = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Transacao other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
