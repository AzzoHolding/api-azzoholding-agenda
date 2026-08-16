package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/fiscal/domain/entity/FiscalCodeCatalogEntity.java}. Tabela
 * {@code fiscal_code_catalog} — catalogo de codigos fiscais (NCM, CFOP, CST...) com vigencia.
 *
 * <p><b>Nao tem {@code tenant_id}</b>: o catalogo e global, compartilhado por todos os tenants.
 * {@code validTo} nulo significa vigencia aberta.
 */
@Entity
@Table(name = "fiscal_code_catalog")
@Getter
@Setter
public class FiscalCodeCatalogEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "code_type", nullable = false)
  private String codeType;

  @Column(name = "code_value", nullable = false)
  private String codeValue;

  @Column(name = "description")
  private String description;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_to")
  private LocalDate validTo;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (status == null || status.isBlank()) status = "ACTIVE";
    if (createdAt == null) createdAt = Instant.now();
  }
}
