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
 * Espelha {@code modules/lgpd/domain/entity/LgpdDataSubjectRequest.java}. Tabela
 * {@code lgpd_data_subject_requests} — solicitacoes de titular de dados (LGPD art. 18).
 */
@Entity
@Table(name = "lgpd_data_subject_requests")
@Getter
@Setter
public class LgpdDataSubjectRequest {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "protocol_code", nullable = false)
  private String protocolCode;

  @Column(name = "request_type", nullable = false)
  private String requestType;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "requester_name", nullable = false)
  private String requesterName;

  @Column(name = "requester_email", nullable = false)
  private String requesterEmail;

  @Column(name = "requester_document")
  private String requesterDocument;

  @Column(name = "description")
  private String description;

  @Column(name = "response_summary")
  private String responseSummary;

  @Column(name = "assigned_to_user_id")
  private UUID assignedToUserId;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LgpdDataSubjectRequest other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
