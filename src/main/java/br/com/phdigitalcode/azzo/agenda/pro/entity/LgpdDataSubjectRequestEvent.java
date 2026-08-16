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
 * Espelha {@code modules/lgpd/domain/entity/LgpdDataSubjectRequestEvent.java}. Tabela
 * {@code lgpd_data_subject_request_events} — historico de eventos de uma solicitacao LGPD.
 */
@Entity
@Table(name = "lgpd_data_subject_request_events")
@Getter
@Setter
public class LgpdDataSubjectRequestEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "previous_status")
  private String previousStatus;

  @Column(name = "new_status")
  private String newStatus;

  @Column(name = "event_note")
  private String eventNote;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

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
    if (!(o instanceof LgpdDataSubjectRequestEvent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
