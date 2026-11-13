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

/** Um aviso da operadora (webhook) ou uma consulta, como chegou (V167). So se insere e marca processado. */
@Entity
@Table(name = "eventos_de_cobranca")
@Getter
@Setter
public class EventoDeCobranca {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "cobranca_id")
  private UUID cobrancaId;

  @Column(name = "conta_id")
  private UUID contaId;

  @Column(name = "provedor", nullable = false)
  private String provedor;

  /** WEBHOOK, CONSULTA ou MANUAL. */
  @Column(name = "origem", nullable = false)
  private String origem;

  @Column(name = "tipo", nullable = false)
  private String tipo;

  @Column(name = "status_informado")
  private String statusInformado;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "payload_hash", nullable = false)
  private String payloadHash;

  @Column(name = "payload_json", columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "processado", nullable = false)
  private boolean processado;

  @Column(name = "erro")
  private String erro;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
