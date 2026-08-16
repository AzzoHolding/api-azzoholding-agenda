package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/pos/domain/entity/ComandaPagamento.java}. Tabela {@code comanda_pagamentos}. */
@Entity
@Table(name = "comanda_pagamentos")
@Getter
@Setter
public class ComandaPagamento {

  public static final String MEIO_DINHEIRO = "DINHEIRO";
  public static final String MEIO_PIX_ASAAS = "PIX_ASAAS";
  public static final String MEIO_CARTAO_CREDITO_EXTERNO = "CARTAO_CREDITO_EXTERNO";
  public static final String MEIO_CARTAO_DEBITO_EXTERNO = "CARTAO_DEBITO_EXTERNO";
  public static final String MEIO_CREDITO_SINAL = "CREDITO_SINAL";

  public static final String STATUS_PENDENTE = "PENDENTE";
  public static final String STATUS_CONFIRMADO = "CONFIRMADO";
  public static final String STATUS_ESTORNADO = "ESTORNADO";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "comanda_id", nullable = false)
  private UUID comandaId;

  @Column(name = "meio", nullable = false)
  private String meio;

  @Column(name = "valor", nullable = false)
  private BigDecimal valor;

  @Column(name = "status", nullable = false)
  private String status = STATUS_PENDENTE;

  @Column(name = "asaas_payment_id")
  private String asaasPaymentId;

  @Column(name = "pix_payload")
  private String pixPayload;

  @Column(name = "appointment_deposit_id")
  private UUID appointmentDepositId;

  @Column(name = "registrado_por")
  private UUID registradoPor;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ComandaPagamento other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
