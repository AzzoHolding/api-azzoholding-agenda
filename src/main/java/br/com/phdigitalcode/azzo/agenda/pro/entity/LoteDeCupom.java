package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Lote do gerador de cupons (V180): N codigos de uso unico com as mesmas regras. */
@Entity
@Table(name = "lotes_de_cupom")
@Getter
@Setter
public class LoteDeCupom {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "nome", nullable = false)
  private String nome;

  @Column(name = "prefixo", nullable = false)
  private String prefixo;

  @Column(name = "quantidade", nullable = false)
  private int quantidade;

  @Column(name = "tipo", nullable = false)
  private String tipo;

  @Column(name = "valor", nullable = false)
  private BigDecimal valor;

  @Column(name = "inicio")
  private LocalDate inicio;

  @Column(name = "fim")
  private LocalDate fim;

  @Column(name = "valor_minimo")
  private BigDecimal valorMinimo;

  @Column(name = "ativo", nullable = false)
  private boolean ativo = true;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant agora = Instant.now();
    if (createdAt == null) createdAt = agora;
    updatedAt = agora;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
