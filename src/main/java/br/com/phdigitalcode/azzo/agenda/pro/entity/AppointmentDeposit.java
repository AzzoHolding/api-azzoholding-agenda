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
 * Espelha {@code modules/scheduling/domain/entity/AppointmentDeposit.java}. Tabela
 * {@code appointment_deposits}.
 *
 * <p>Sinal (deposito antecipado) cobrado na conta Asaas do proprio tenant para confirmar uma
 * reserva de agendamento publico. Status local, desacoplado do enum de cobranca da PLATAFORMA sobre
 * o tenant.
 *
 * <p>Portada junto com {@code pos} porque {@code ServicoComanda} consome o sinal como credito
 * ({@code CREDITO_SINAL}); o restante do modulo {@code scheduling} ainda nao foi migrado.
 */
@Entity
@Table(name = "appointment_deposits")
@Getter
@Setter
public class AppointmentDeposit {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PAID = "PAID";
  public static final String STATUS_EXPIRED = "EXPIRED";
  public static final String STATUS_CANCELLED = "CANCELLED";

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "asaas_payment_id", nullable = false, unique = true)
  private String asaasPaymentId;

  @Column(name = "status", nullable = false)
  private String status = STATUS_PENDING;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(name = "pix_payload")
  private String pixPayload;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  /** Comanda que consumiu este sinal como credito (CREDITO_SINAL) — evita reuso duplicado. */
  @Column(name = "used_in_comanda_id")
  private UUID usedInComandaId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AppointmentDeposit other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
