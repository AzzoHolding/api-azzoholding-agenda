package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/fiscal/domain/entity/FiscalSequenceControlEntity.java}. Tabela
 * {@code fiscal_sequence_control} — a numeracao sequencial do documento fiscal por
 * tenant/modelo/serie/ambiente, garantida pela unique {@code uq_fiscal_sequence_control}.
 *
 * <p>O {@code @PrePersist} zera {@code ultimoNumero} quando nulo: o primeiro documento emitido
 * recebe 1.
 */
@Entity
@Table(
    name = "fiscal_sequence_control",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_fiscal_sequence_control",
            columnNames = {"tenant_id", "modelo", "serie", "ambiente"}))
@Getter
@Setter
public class FiscalSequenceControlEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "modelo", nullable = false)
  private String modelo;

  @Column(name = "serie", nullable = false)
  private Integer serie;

  @Column(name = "ambiente", nullable = false)
  private String ambiente;

  @Column(name = "ultimo_numero", nullable = false)
  private Integer ultimoNumero;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (ultimoNumero == null) ultimoNumero = 0;
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
