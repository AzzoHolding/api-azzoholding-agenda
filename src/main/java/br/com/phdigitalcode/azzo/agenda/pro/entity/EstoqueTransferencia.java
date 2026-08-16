package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusTransferenciaEstoque;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/inventory/domain/entity/EstoqueTransferencia.java}. Tabela
 * {@code estoque_transferencia}.
 *
 * <p>Os {@code @ManyToOne} para {@code Tenant} e {@code ItemEstoque} do original nao foram mapeados
 * (acesso sempre pelo id escalar); o {@code itemNome} do DTO entra por parametro no mapper.
 *
 * <p><b>A transferencia nao movimenta saldo</b> em nenhum dos tres endpoints do original — e um
 * registro de status puro. Preservado.
 */
@Entity
@Table(name = "estoque_transferencia")
@Getter
@Setter
public class EstoqueTransferencia {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "origem", nullable = false, length = 120)
  private String origem;

  @Column(name = "destino", nullable = false, length = 120)
  private String destino;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StatusTransferenciaEstoque status;

  @Column(name = "item_estoque_id", nullable = false)
  private UUID itemEstoqueId;

  @Column(name = "quantidade", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantidade;

  @Column(name = "observacao", length = 500)
  private String observacao;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null) status = StatusTransferenciaEstoque.RASCUNHO;
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EstoqueTransferencia other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
