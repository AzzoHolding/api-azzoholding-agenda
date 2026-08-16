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
 * Espelha {@code modules/inventory/domain/entity/ItemEstoque.java}. Tabela {@code itens_estoque}.
 *
 * <p>O {@code @ManyToOne Tenant} do original nao foi mapeado — mesma decisao ja tomada para as
 * demais entidades portadas: todo acesso filtra por {@code tenantId} escalar e nunca navega o grafo
 * a partir do tenant.
 */
@Entity
@Table(name = "itens_estoque")
@Getter
@Setter
public class ItemEstoque {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "nome", nullable = false, length = 160)
  private String nome;

  @Column(name = "sku", length = 80)
  private String sku;

  @Column(name = "unidade_medida", nullable = false, length = 20)
  private String unidadeMedida;

  @Column(name = "saldo_atual", nullable = false, precision = 19, scale = 4)
  private BigDecimal saldoAtual;

  @Column(name = "estoque_minimo", nullable = false, precision = 19, scale = 4)
  private BigDecimal estoqueMinimo;

  @Column(name = "custo_medio_unitario", precision = 19, scale = 4)
  private BigDecimal custoMedioUnitario;

  @Column(name = "ativo", nullable = false)
  private Boolean ativo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (saldoAtual == null) saldoAtual = BigDecimal.ZERO;
    if (estoqueMinimo == null) estoqueMinimo = BigDecimal.ZERO;
    if (ativo == null) ativo = Boolean.TRUE;
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
    if (!(o instanceof ItemEstoque other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
