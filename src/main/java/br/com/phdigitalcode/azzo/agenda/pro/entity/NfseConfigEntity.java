package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
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
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCpfPolicyMode;
import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseEmissionMode;

/**
 * Espelha {@code modules/nfse/domain/entity/NfseConfigEntity.java}. Tabela {@code nfse_configs} —
 * configuracao de NFS-e por tenant+ambiente (unico por par, ver
 * {@code uq_nfse_configs_tenant_ambiente}), serie RPS, aliquota padrao e campos especificos do
 * SEFIN Nacional ({@code applicationVersion}, {@code nationalTaxCodeDefault}, etc., adicionados por
 * {@code V18__add_sefin_nacional_nfse_fields.sql}).
 */
@Entity
@Table(name = "nfse_configs")
@Getter
@Setter
public class NfseConfigEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "ambiente", nullable = false)
  private NfseAmbiente ambiente;

  @Column(name = "municipio_codigo_ibge", nullable = false)
  private String municipioCodigoIbge;

  @Column(name = "provedor", nullable = false)
  private String provedor;

  @Column(name = "serie_rps", nullable = false)
  private String serieRps;

  @Column(name = "aliquota_iss_padrao", nullable = false)
  private BigDecimal aliquotaIssPadrao;

  @Column(name = "item_lista_servico_padrao", nullable = false)
  private String itemListaServicoPadrao;

  @Column(name = "codigo_tributacao_municipio")
  private String codigoTributacaoMunicipio;

  @Column(name = "application_version")
  private String applicationVersion;

  @Column(name = "national_tax_code_default")
  private String nationalTaxCodeDefault;

  @Column(name = "nbs_code_default")
  private String nbsCodeDefault;

  @Column(name = "simples_nacional_situacao")
  private String simplesNacionalSituacao;

  @Column(name = "simples_nacional_regime_tributacao")
  private String simplesNacionalRegimeTributacao;

  @Column(name = "especial_regime_tributacao")
  private String especialRegimeTributacao;

  @Column(name = "ws_url", length = 512)
  private String wsUrl;

  @Column(name = "ws_url_homologacao", length = 512)
  private String wsUrlHomologacao;

  @Enumerated(EnumType.STRING)
  @Column(name = "emission_mode", nullable = false)
  private NfseEmissionMode emissionMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "emit_for_cpf_mode", nullable = false)
  private NfseCpfPolicyMode emitForCpfMode;

  @Column(name = "auto_issue_on_appointment_close", nullable = false)
  private boolean autoIssueOnAppointmentClose;

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
