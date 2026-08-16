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

/** Espelha {@code domain/entity/IntegrationLog.java}. Tabela {@code integration_logs}. */
@Entity
@Table(name = "integration_logs")
@Getter
@Setter
public class IntegrationLog {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "direction", nullable = false)
  private String direction;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "external_reference")
  private String externalReference;

  @Column(name = "request_payload")
  private String requestPayload;

  @Column(name = "response_payload")
  private String responsePayload;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
