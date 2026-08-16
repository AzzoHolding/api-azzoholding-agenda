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

/** Espelha {@code modules/inventory/domain/entity/ServicoInsumo.java}. Tabela {@code servico_insumo}. */
@Entity
@Table(name = "servico_insumo")
@Getter
@Setter
public class ServicoInsumo {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "item_estoque_id", nullable = false)
  private UUID itemEstoqueId;

  /** Quantidade consumida por execucao do servico (antes da perda). */
  @Column(name = "quantidade_consumo", nullable = false, precision = 19, scale = 4)
  private BigDecimal quantidadeConsumo;

  /** Percentual de perda esperado (0-100). Ex: 5.00 = 5%. */
  @Column(name = "percentual_perda", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentualPerda;

  @Column(name = "ativo", nullable = false)
  private Boolean ativo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (percentualPerda == null) percentualPerda = BigDecimal.ZERO;
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
    if (!(o instanceof ServicoInsumo other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
