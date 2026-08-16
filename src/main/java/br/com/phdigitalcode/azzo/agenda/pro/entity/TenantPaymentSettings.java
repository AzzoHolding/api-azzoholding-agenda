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
 * Espelha {@code domain/entity/TenantPaymentSettings.java}. Tabela
 * {@code tenant_payment_settings} — a PK e o proprio {@code tenant_id} (1:1 com o tenant), sem
 * coluna {@code id} separada.
 */
@Entity
@Table(name = "tenant_payment_settings")
@Getter
@Setter
public class TenantPaymentSettings {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "provider", nullable = false)
  private String provider = "ASAAS";

  @Column(name = "ambiente", nullable = false)
  private String ambiente = "SANDBOX";

  @Column(name = "ativo", nullable = false)
  private boolean ativo = false;

  @Column(name = "api_key_enc")
  private String apiKeyEnc;

  @Column(name = "webhook_token")
  private String webhookToken;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantPaymentSettings other)) return false;
    return tenantId != null && tenantId.equals(other.tenantId);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
