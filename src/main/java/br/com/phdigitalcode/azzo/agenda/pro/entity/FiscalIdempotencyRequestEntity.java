package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/fiscal/domain/entity/FiscalIdempotencyRequestEntity.java}. Tabela
 * {@code fiscal_idempotency_requests} — guarda a resposta ja serializada de uma operacao fiscal
 * para que o mesmo {@code Idempotency-Key} devolva o mesmo corpo em vez de emitir outro documento.
 */
@Entity
@Table(name = "fiscal_idempotency_requests")
@Getter
@Setter
public class FiscalIdempotencyRequestEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "operation", nullable = false)
  private String operation;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "response_json", nullable = false)
  private String responseJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
