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
 * Espelha {@code modules/fiscal/domain/entity/FiscalInvoiceEventEntity.java}. Tabela
 * {@code fiscal_invoice_events} — trilha append-only de eventos do documento fiscal, sem
 * {@code updated_at} e sem {@code @PreUpdate}, como no original.
 */
@Entity
@Table(name = "fiscal_invoice_events")
@Getter
@Setter
public class FiscalInvoiceEventEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "event_status", nullable = false)
  private String eventStatus;

  @Column(name = "sefaz_status_code")
  private String sefazStatusCode;

  @Column(name = "sefaz_status_message")
  private String sefazStatusMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }
}
