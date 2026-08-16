package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;
import java.time.LocalDate;
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
 * Espelha {@code modules/fiscal/domain/entity/FiscalInvoiceEntity.java}. Tabela
 * {@code fiscal_invoices}.
 *
 * <p><b>{@code status} e {@code String}, nao enum</b> — como no original. O
 * {@code FiscalDocumentStatus} existe e e usado nas regras, mas a coluna guarda texto livre e o
 * parsing passa por {@code FiscalDocumentStatus.tryParse}, que tolera valor desconhecido devolvendo
 * {@code Optional.empty()}. Mapear como {@code @Enumerated} quebraria a leitura de qualquer linha
 * legada com valor fora do enum.
 *
 * <p>{@code totalAmount} e {@code long} em centavos, como no original.
 */
@Entity
@Table(name = "fiscal_invoices")
@Getter
@Setter
public class FiscalInvoiceEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "external_invoice_id", nullable = false)
  private String externalInvoiceId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Column(name = "invoice_type")
  private String invoiceType;

  @Column(name = "modelo")
  private String modelo;

  @Column(name = "serie")
  private Integer serie;

  @Column(name = "numero")
  private Integer numero;

  @Column(name = "ambiente")
  private String ambiente;

  @Column(name = "status")
  private String status;

  @Column(name = "numero_nf")
  private String numeroNf;

  @Column(name = "serie_nf")
  private String serieNf;

  @Column(name = "chave_acesso")
  private String chaveAcesso;

  @Column(name = "protocolo_autorizacao")
  private String protocoloAutorizacao;

  @Column(name = "data_emissao")
  private LocalDate dataEmissao;

  @Column(name = "total_amount", nullable = false)
  private long totalAmount;

  @Column(name = "payload_json")
  private String payloadJson;

  @Column(name = "xml_signed_enc")
  private String xmlSignedEnc;

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
