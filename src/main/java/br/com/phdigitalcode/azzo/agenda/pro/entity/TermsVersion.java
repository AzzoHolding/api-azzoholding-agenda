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
 * Espelha {@code domain/entity/TermsVersion.java} (raiz do dominio no Quarkus, consumida pelo
 * modulo {@code audit} via {@code TermsService}). Tabela {@code terms_versions}.
 */
@Entity
@Table(name = "terms_versions")
@Getter
@Setter
public class TermsVersion {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "document_type", nullable = false)
  private String documentType;

  @Column(name = "version", nullable = false)
  private String version;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "content", nullable = false)
  private String content;

  @Column(name = "content_hash", nullable = false)
  private String contentHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_by")
  private UUID publishedBy;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TermsVersion other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
