package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/inventory/domain/entity/EstoqueInventarioContagem.java}. Tabela
 * {@code estoque_inventario_contagem}.
 *
 * <p>Os {@code @ManyToOne} para {@code EstoqueInventario} e {@code ItemEstoque} do original nao
 * foram mapeados (acesso sempre pelo id escalar); o nome/unidade do item entram no DTO por
 * parametro no mapper, resolvidos em lote.
 *
 * <p><b>Nao ha {@code @PreUpdate}</b> — no original tambem nao. Quem carimba {@code updatedAt} e
 * {@code ServicoEstoque.atualizarContagemInventario}, explicitamente.
 */
@Entity
@Table(name = "estoque_inventario_contagem")
@Getter
@Setter
public class EstoqueInventarioContagem {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "inventario_id", nullable = false)
  private UUID inventarioId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "item_estoque_id", nullable = false)
  private UUID itemEstoqueId;

  @Column(name = "quantidade_contada", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantidadeContada;

  @Column(name = "observacao", length = 500)
  private String observacao;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "quantidade_esperada", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantidadeEsperada;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "usuario_atualizacao_id")
  private UUID usuarioAtualizacaoId;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (quantidadeEsperada == null) quantidadeEsperada = BigDecimal.ZERO;
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof EstoqueInventarioContagem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
