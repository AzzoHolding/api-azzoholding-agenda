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
 * Espelha {@code modules/scheduling/domain/entity/AppointmentCustomerNote.java}. Tabela
 * {@code appointment_customer_notes}.
 */
@Entity
@Table(name = "appointment_customer_notes")
@Getter
@Setter
public class AppointmentCustomerNote {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id", nullable = false)
  private UUID appointmentId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  @Column(name = "recorded_by_user_id")
  private UUID recordedByUserId;

  @Column(name = "service_execution_notes", length = 1000)
  private String serviceExecutionNotes;

  @Column(name = "client_feedback_notes", length = 1000)
  private String clientFeedbackNotes;

  @Column(name = "internal_followup_notes", length = 1000)
  private String internalFollowupNotes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

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
    if (!(o instanceof AppointmentCustomerNote other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
