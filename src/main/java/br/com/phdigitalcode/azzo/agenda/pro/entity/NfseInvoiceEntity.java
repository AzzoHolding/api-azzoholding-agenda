package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseAmbiente;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCustomerType;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseFiscalStatus;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseOperationalStatus;

/**
 * Espelha {@code modules/nfse/domain/entity/NfseInvoiceEntity.java} (165L no original) — entidade
 * central do modulo, RPS/NFS-e. {@code xmlEnvioEnc}/{@code xmlRetornoEnc} guardam o XML
 * <b>cifrado</b> como {@code byte[]} (sufixo {@code _enc}, mesmo padrao de {@code
 * FiscalInvoiceEntity}). Chave natural unica: {@code (tenant, municipio, serie, numeroRps,
 * ambiente)} — ver {@code uq_nfse_invoices_tenant_municipio_serie_numero_ambiente}.
 */
@Entity
@Table(name = "nfse_invoices")
@Getter
@Setter
public class NfseInvoiceEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "customer_type", nullable = false)
  private NfseCustomerType customerType;

  @Column(name = "customer_document")
  private String customerDocument;

  @Column(name = "customer_country_code")
  private String customerCountryCode;

  @Column(name = "customer_document_type")
  private String customerDocumentType;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "customer_email")
  private String customerEmail;

  @Column(name = "customer_phone")
  private String customerPhone;

  @Enumerated(EnumType.STRING)
  @Column(name = "fiscal_status", nullable = false)
  private NfseFiscalStatus fiscalStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "operational_status")
  private NfseOperationalStatus operationalStatus;

  @Column(name = "municipio_codigo_ibge", nullable = false)
  private String municipioCodigoIbge;

  @Column(name = "provedor", nullable = false)
  private String provedor;

  @Enumerated(EnumType.STRING)
  @Column(name = "ambiente", nullable = false)
  private NfseAmbiente ambiente;

  @Column(name = "numero_rps", nullable = false)
  private Long numeroRps;

  @Column(name = "serie_rps", nullable = false)
  private String serieRps;

  @Column(name = "numero_nfse")
  private String numeroNfse;

  @Column(name = "codigo_verificacao")
  private String codigoVerificacao;

  @Column(name = "chave_acesso_nfse")
  private String chaveAcessoNfse;

  @Column(name = "protocolo")
  private String protocolo;

  @Column(name = "data_competencia", nullable = false)
  private LocalDate dataCompetencia;

  @Column(name = "data_emissao")
  private Instant dataEmissao;

  @Column(name = "natureza_operacao", nullable = false)
  private String naturezaOperacao;

  @Column(name = "item_lista_servico", nullable = false)
  private String itemListaServico;

  @Column(name = "codigo_tributacao_municipio")
  private String codigoTributacaoMunicipio;

  @Column(name = "national_tax_code")
  private String nationalTaxCode;

  @Column(name = "nbs_code")
  private String nbsCode;

  @Column(name = "local_prestacao_codigo_ibge")
  private String localPrestacaoCodigoIbge;

  @Column(name = "valor_servicos", nullable = false)
  private BigDecimal valorServicos;

  @Column(name = "valor_deducoes", nullable = false)
  private BigDecimal valorDeducoes;

  @Column(name = "valor_iss", nullable = false)
  private BigDecimal valorIss;

  @Column(name = "aliquota_iss", nullable = false)
  private BigDecimal aliquotaIss;

  @Column(name = "iss_retido", nullable = false)
  private boolean issRetido;

  @Column(name = "xml_envio_enc")
  private byte[] xmlEnvioEnc;

  @Column(name = "xml_retorno_enc")
  private byte[] xmlRetornoEnc;

  @Column(name = "pdf_storage_key")
  private String pdfStorageKey;

  @Column(name = "pdf_generated_at")
  private Instant pdfGeneratedAt;

  @Column(name = "notes")
  private String notes;

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
