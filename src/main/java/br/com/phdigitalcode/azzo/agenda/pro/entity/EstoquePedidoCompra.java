package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusPedidoCompraEstoque;
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
 * Espelha {@code modules/inventory/domain/entity/EstoquePedidoCompra.java}. Tabela
 * {@code estoque_pedido_compra}.
 *
 * <p>Os {@code @ManyToOne} para {@code Tenant} e {@code EstoqueFornecedor} do original nao foram
 * mapeados (acesso sempre pelo id escalar); o {@code fornecedorNome} do DTO entra por parametro no
 * mapper, resolvido em lote na listagem.
 */
@Entity
@Table(name = "estoque_pedido_compra")
@Getter
@Setter
public class EstoquePedidoCompra {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "fornecedor_id", nullable = false)
  private UUID fornecedorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private StatusPedidoCompraEstoque status;

  @Column(name = "valor_total", nullable = false, precision = 19, scale = 4)
  private BigDecimal valorTotal;

  @Column(name = "quantidade_itens", nullable = false)
  private Integer quantidadeItens;

  @Column(name = "quantidade_pendente", nullable = false)
  private Integer quantidadePendente;

  @Column(name = "observacao", length = 500)
  private String observacao;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null) status = StatusPedidoCompraEstoque.RASCUNHO;
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
    if (!(o instanceof EstoquePedidoCompra other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
