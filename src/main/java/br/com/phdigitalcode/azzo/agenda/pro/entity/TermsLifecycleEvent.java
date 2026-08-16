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
 * Espelha {@code domain/entity/TermsLifecycleEvent.java}. Tabela {@code terms_lifecycle_events} —
 * historico de publicacao/desativacao de versoes de termos, consumido por {@code TermsService}.
 */
@Entity
@Table(name = "terms_lifecycle_events")
@Getter
@Setter
public class TermsLifecycleEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "terms_version_id", nullable = false)
  private UUID termsVersionId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "event_metadata_json")
  private String eventMetadataJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TermsLifecycleEvent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
