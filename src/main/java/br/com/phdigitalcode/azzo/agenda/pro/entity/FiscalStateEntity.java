package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/nfse/domain/entity/FiscalStateEntity.java}. Tabela {@code fiscal_states}
 * — catalogo IBGE de UFs, pre-carregado por {@code V20__create_fiscal_states_and_municipalities.sql}.
 * Pertence ao pacote {@code fiscal} no original, mas so e usado por {@code nfse}.
 */
@Entity
@Table(name = "fiscal_states")
@Getter
@Setter
public class FiscalStateEntity {

  @Id
  @Column(name = "codigo_ibge", nullable = false, length = 2)
  private String codigoIbge;

  @Column(name = "uf", nullable = false, length = 2, unique = true)
  private String uf;

  @Column(name = "nome", nullable = false, length = 100)
  private String nome;

  @Column(name = "regiao_sigla", length = 2)
  private String regiaoSigla;

  @Column(name = "regiao_nome", length = 30)
  private String regiaoNome;

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
