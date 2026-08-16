package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/nfse/domain/entity/FiscalMunicipalityEntity.java}. Tabela
 * {@code fiscal_municipalities} — catalogo IBGE/TOM de municipios, FK para {@link
 * FiscalStateEntity}. Nao ha FK direta com {@code nfse_configs}/{@code nfse_invoices}: essas
 * tabelas guardam {@code municipio_codigo_ibge} como string solta (confirmado no original e na
 * migration V1, que so cria as FKs como {@code NOT VALID}).
 */
@Entity
@Table(name = "fiscal_municipalities")
@Getter
@Setter
public class FiscalMunicipalityEntity {

  @Id
  @Column(name = "codigo_ibge", nullable = false, length = 7)
  private String codigoIbge;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "state_codigo_ibge", referencedColumnName = "codigo_ibge", nullable = false)
  private FiscalStateEntity state;

  @Column(name = "nome", nullable = false, length = 120)
  private String nome;

  @Column(name = "codigo_tom", length = 4)
  private String codigoTom;

  @Column(name = "codigo_tom_dv", length = 1)
  private String codigoTomDv;

  @Column(name = "codigo_tom_com_dv", length = 5)
  private String codigoTomComDv;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
