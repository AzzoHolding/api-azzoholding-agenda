package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
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
 * Espelha {@code modules/nfse/domain/entity/NfseInvoiceItemEntity.java}. Tabela
 * {@code nfse_invoice_items} — itens de servico da NFS-e, chave natural {@code (invoiceId,
 * lineNumber)} unica.
 */
@Entity
@Table(name = "nfse_invoice_items")
@Getter
@Setter
public class NfseInvoiceItemEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "line_number", nullable = false)
  private Integer lineNumber;

  @Column(name = "descricao_servico", nullable = false)
  private String descricaoServico;

  @Column(name = "quantidade", nullable = false)
  private BigDecimal quantidade;

  @Column(name = "valor_unitario", nullable = false)
  private BigDecimal valorUnitario;

  @Column(name = "valor_total", nullable = false)
  private BigDecimal valorTotal;

  @Column(name = "item_lista_servico", nullable = false)
  private String itemListaServico;

  @Column(name = "codigo_tributacao_municipio")
  private String codigoTributacaoMunicipio;

  @Column(name = "national_tax_code")
  private String nationalTaxCode;

  @Column(name = "nbs_code")
  private String nbsCode;

  @Column(name = "aliquota_iss", nullable = false)
  private BigDecimal aliquotaIss;

  @Column(name = "valor_iss", nullable = false)
  private BigDecimal valorIss;

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
