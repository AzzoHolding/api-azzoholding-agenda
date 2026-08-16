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
 * Espelha {@code modules/fiscal/domain/entity/FiscalTaxConfigEntity.java}. Tabela
 * {@code fiscal_tax_configs} — uma linha por tenant ({@code tenant_id} e {@code unique}).
 *
 * <p>Guarda tanto as aliquotas quanto o cadastro completo do emitente e os CSC de NFC-e
 * (homologacao e producao), que sao segredo e nunca devem sair em resposta de API.
 */
@Entity
@Table(name = "fiscal_tax_configs")
@Getter
@Setter
public class FiscalTaxConfigEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  private UUID tenantId;

  @Column(name = "regime")
  private String regime;

  @Column(name = "icms_rate", nullable = false)
  private BigDecimal icmsRate;

  @Column(name = "pis_rate", nullable = false)
  private BigDecimal pisRate;

  @Column(name = "cofins_rate", nullable = false)
  private BigDecimal cofinsRate;

  @Column(name = "issuer_razao_social")
  private String issuerRazaoSocial;

  @Column(name = "issuer_nome_fantasia")
  private String issuerNomeFantasia;

  @Column(name = "issuer_cnpj")
  private String issuerCnpj;

  @Column(name = "issuer_ie")
  private String issuerIe;

  @Column(name = "issuer_im")
  private String issuerIm;

  @Column(name = "issuer_phone")
  private String issuerPhone;

  @Column(name = "issuer_email")
  private String issuerEmail;

  @Column(name = "issuer_street")
  private String issuerStreet;

  @Column(name = "issuer_number")
  private String issuerNumber;

  @Column(name = "issuer_complement")
  private String issuerComplement;

  @Column(name = "issuer_neighborhood")
  private String issuerNeighborhood;

  @Column(name = "issuer_city")
  private String issuerCity;

  @Column(name = "issuer_state")
  private String issuerState;

  @Column(name = "issuer_zip_code")
  private String issuerZipCode;

  @Column(name = "issuer_uf_code")
  private String issuerUfCode;

  @Column(name = "nfce_csc_homologation")
  private String nfceCscHomologation;

  @Column(name = "nfce_csc_id_token_homologation")
  private String nfceCscIdTokenHomologation;

  @Column(name = "nfce_csc_production")
  private String nfceCscProduction;

  @Column(name = "nfce_csc_id_token_production")
  private String nfceCscIdTokenProduction;

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
