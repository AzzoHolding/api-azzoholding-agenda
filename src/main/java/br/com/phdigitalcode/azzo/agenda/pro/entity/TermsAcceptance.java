package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/onboarding/domain/entity/TermsAcceptanceEntity.java}. Tabela
 * {@code terms_acceptance} (ja existente no schema, {@code V1__baseline_unified.sql}).
 */
@Entity
@Table(name = "terms_acceptance")
@Getter
@Setter
public class TermsAcceptance {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "terms_version", nullable = false, length = 20)
  private String termsVersion;

  @Column(name = "privacy_version", nullable = false, length = 20)
  private String privacyVersion;

  @Column(name = "accepted_at", nullable = false)
  private LocalDateTime acceptedAt;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (acceptedAt == null) acceptedAt = LocalDateTime.now();
  }
}
