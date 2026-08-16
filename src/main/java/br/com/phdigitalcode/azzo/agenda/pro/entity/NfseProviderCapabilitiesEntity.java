package br.com.phdigitalcode.azzo.agenda.pro.entity;

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

import br.com.phdigitalcode.azzo.agenda.pro.entity.enums.NfseCancelMode;

/**
 * Espelha {@code modules/nfse/domain/entity/NfseProviderCapabilitiesEntity.java}. Tabela
 * {@code nfse_provider_capabilities} — capacidades por {@code (municipio, provedor,
 * layoutVersion)}, usada para decidir se cancelamento e suportado/sincrono e a janela de
 * cancelamento.
 */
@Entity
@Table(name = "nfse_provider_capabilities")
@Getter
@Setter
public class NfseProviderCapabilitiesEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "municipio_codigo_ibge", nullable = false)
  private String municipioCodigoIbge;

  @Column(name = "provedor", nullable = false)
  private String provedor;

  @Column(name = "layout_version", nullable = false)
  private String layoutVersion;

  @Column(name = "cancel_supported", nullable = false)
  private boolean cancelSupported;

  @Column(name = "cancel_window_hours")
  private Integer cancelWindowHours;

  @Enumerated(EnumType.STRING)
  @Column(name = "cancel_mode", nullable = false)
  private NfseCancelMode cancelMode;

  @Column(name = "accepted_cancel_reason_codes")
  private String acceptedCancelReasonCodes;

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
