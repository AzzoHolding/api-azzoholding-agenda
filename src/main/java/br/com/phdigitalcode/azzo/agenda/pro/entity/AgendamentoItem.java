package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/scheduling/domain/entity/AgendamentoItem.java}. Tabela
 * {@code appointment_items}.
 *
 * <p>{@code service} e mantido como {@code @ManyToOne} (o original o usa em varias respostas e no
 * calculo de duracao/comissao). A associacao {@code tenant} e {@code appointment} do original virou
 * apenas coluna escalar — nunca sao navegadas a partir do item.
 */
@Entity
@Table(name = "appointment_items")
@Getter
@Setter
public class AgendamentoItem {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  /**
   * Join composto (tenant_id + service_id) igual ao original — garante que o servico carregado e
   * do mesmo tenant do item.
   */
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumns({
    @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id", insertable = false, updatable = false),
    @JoinColumn(name = "service_id", referencedColumnName = "id", insertable = false, updatable = false)
  })
  private Servico service;

  @Column(name = "quantity", nullable = false)
  private int quantity = 1;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  @Column(name = "gross_amount", nullable = false)
  private BigDecimal grossAmount;

  @Column(name = "discount_amount", nullable = false)
  private BigDecimal discountAmount;

  @Column(name = "total_price", nullable = false)
  private BigDecimal totalPrice;

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
    if (!(o instanceof AgendamentoItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
