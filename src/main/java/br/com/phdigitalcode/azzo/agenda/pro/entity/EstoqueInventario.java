package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.StatusInventarioEstoque;
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
 * Espelha {@code modules/inventory/domain/entity/EstoqueInventario.java}. Tabela
 * {@code estoque_inventario}.
 *
 * <p>O {@code @ManyToOne Tenant} do original nao foi mapeado (acesso sempre pelo id escalar), mesma
 * decisao das demais entidades portadas.
 *
 * <p>O default de {@code status} no {@code @PrePersist} e {@code ABERTO}, mas
 * {@code ServicoEstoque.criarInventario} sempre grava {@code EM_CONTAGEM} — ou seja, o default
 * nunca e exercido pela superficie HTTP. Assimetria do original, preservada.
 */
@Entity
@Table(name = "estoque_inventario")
@Getter
@Setter
public class EstoqueInventario {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "nome", nullable = false, length = 200)
  private String nome;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StatusInventarioEstoque status;

  @Column(name = "observacao", length = 500)
  private String observacao;

  @Column(name = "data_abertura", nullable = false)
  private Instant dataAbertura;

  @Column(name = "data_fechamento")
  private Instant dataFechamento;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null) status = StatusInventarioEstoque.ABERTO;
    if (dataAbertura == null) dataAbertura = Instant.now();
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
    if (!(o instanceof EstoqueInventario other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
