package br.com.phdigitalcode.azzo.agenda.pro.entity;

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
 * Espelha {@code modules/inventory/domain/entity/EstoqueFornecedor.java}. Tabela
 * {@code estoque_fornecedor}.
 *
 * <p>O {@code @ManyToOne Tenant} do original nao foi mapeado (acesso sempre pelo id escalar).
 */
@Entity
@Table(name = "estoque_fornecedor")
@Getter
@Setter
public class EstoqueFornecedor {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "nome", nullable = false, length = 200)
  private String nome;

  @Column(name = "documento", length = 40)
  private String documento;

  @Column(name = "email", length = 160)
  private String email;

  @Column(name = "telefone", length = 30)
  private String telefone;

  @Column(name = "contato", length = 160)
  private String contato;

  @Column(name = "ativo", nullable = false)
  private Boolean ativo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
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
    if (!(o instanceof EstoqueFornecedor other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
