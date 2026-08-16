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
 * Espelha {@code domain/entity/ProductCapability.java}. Tabela {@code product_capabilities}.
 *
 * <p><b>Armadilha</b>: a PK e {@code product_id}, nao {@code id}.
 */
@Entity
@Table(name = "product_capabilities")
@Getter
@Setter
public class ProductCapability {

  @Id
  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "max_professionals")
  private Integer maxProfessionals;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
