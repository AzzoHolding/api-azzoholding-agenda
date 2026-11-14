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

/** Quanto a operadora cobra e em quantos dias paga, por meio e faixa de parcelas (V169). */
@Entity
@Table(name = "taxas_de_pagamento")
@Getter
@Setter
public class TaxaDePagamento {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "conta_id", nullable = false)
  private UUID contaId;

  /** PIX, CREDITO, DEBITO, LINK. */
  @Column(name = "meio", nullable = false)
  private String meio;

  @Column(name = "parcelas_de", nullable = false)
  private int parcelasDe = 1;

  @Column(name = "parcelas_ate", nullable = false)
  private int parcelasAte = 1;

  /** Percentual sobre o valor (ex.: 3.490 = 3,49%). */
  @Column(name = "percentual", nullable = false)
  private BigDecimal percentual = BigDecimal.ZERO;

  @Column(name = "valor_fixo", nullable = false)
  private BigDecimal valorFixo = BigDecimal.ZERO;

  @Column(name = "dias_para_receber", nullable = false)
  private int diasParaReceber;

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
