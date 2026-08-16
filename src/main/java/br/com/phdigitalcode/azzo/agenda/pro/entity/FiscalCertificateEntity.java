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
 * Espelha {@code modules/fiscal/domain/entity/FiscalCertificateEntity.java}. Tabela
 * {@code fiscal_certificates} — certificado digital A1 do tenant.
 *
 * <p>{@code certificatePfxEnc} guarda o PFX <b>cifrado</b> (sufixo {@code _enc} do original) e
 * nunca deve aparecer em resposta de API. {@code status} e texto livre: {@code ACTIVE} /
 * {@code INACTIVE}.
 */
@Entity
@Table(name = "fiscal_certificates")
@Getter
@Setter
public class FiscalCertificateEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "certificate_pfx_enc", nullable = false)
  private String certificatePfxEnc;

  @Column(name = "thumbprint", nullable = false)
  private String thumbprint;

  @Column(name = "subject_name", nullable = false)
  private String subjectName;

  @Column(name = "valid_to", nullable = false)
  private Instant validTo;

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
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
