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
 * Espelha {@code modules/nfse/domain/entity/NfseIdempotencyRequestEntity.java}. Tabela
 * {@code nfse_idempotency_requests} — chave unica {@code (tenant, operation, idempotencyKey)},
 * cacheia {@code response_json} da chamada original para respostas idempotentes de
 * {@code authorize}/{@code cancel} (202, via header {@code X-Idempotency-Key}).
 */
@Entity
@Table(name = "nfse_idempotency_requests")
@Getter
@Setter
public class NfseIdempotencyRequestEntity {

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
