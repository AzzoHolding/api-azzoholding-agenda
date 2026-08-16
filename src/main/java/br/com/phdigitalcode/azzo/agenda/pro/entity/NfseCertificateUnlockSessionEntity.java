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
 * Espelha {@code modules/nfse/domain/entity/NfseCertificateUnlockSessionEntity.java}. Tabela
 * {@code nfse_certificate_unlock_sessions} — sessao de "senha do certificado desbloqueada" com
 * TTL, gerenciada por {@code NfseCertificateUnlockService} (Fronteira 4). {@code passwordEnc}
 * guarda a senha cifrada via {@code EncryptionService} (coluna adicionada por
 * {@code V69__add_password_enc_to_nfse_unlock_sessions.sql}). {@code status} texto livre:
 * {@code ACTIVE}/{@code REVOKED}/{@code EXPIRED}.
 */
@Entity
@Table(name = "nfse_certificate_unlock_sessions")
@Getter
@Setter
public class NfseCertificateUnlockSessionEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "certificate_id", nullable = false)
  private UUID certificateId;

  @Column(name = "unlock_token_id", nullable = false)
  private String unlockTokenId;

  @Column(name = "password_enc", columnDefinition = "TEXT")
  private String passwordEnc;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
    if (updatedAt == null) updatedAt = createdAt;
    if (status == null || status.isBlank()) status = "ACTIVE";
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
