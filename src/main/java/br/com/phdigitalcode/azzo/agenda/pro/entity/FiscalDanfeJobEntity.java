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
 * Espelha {@code modules/fiscal/domain/entity/FiscalDanfeJobEntity.java}. Tabela
 * {@code fiscal_danfe_jobs} — fila de geracao assincrona do PDF do DANFE.
 *
 * <p>O marco de tempo de criacao chama-se {@code requested_at} (nao {@code created_at}) e o
 * {@code @PrePersist} tambem aplica os defaults {@code QUEUED} e {@code downloadCount = 0}, como no
 * original.
 */
@Entity
@Table(name = "fiscal_danfe_jobs")
@Getter
@Setter
public class FiscalDanfeJobEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "pdf_storage_key")
  private String pdfStorageKey;

  @Column(name = "download_count", nullable = false)
  private Integer downloadCount;

  @Column(name = "downloaded_at")
  private Instant downloadedAt;

  @Column(name = "download_expires_at")
  private Instant downloadExpiresAt;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (requestedAt == null) requestedAt = Instant.now();
    if (status == null || status.isBlank()) status = "QUEUED";
    if (downloadCount == null) downloadCount = 0;
  }
}
