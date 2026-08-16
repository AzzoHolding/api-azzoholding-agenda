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
 * Espelha {@code domain/entity/TenantLoyaltySettings.java}. Tabela
 * {@code tenant_loyalty_settings} — PK e o proprio {@code tenant_id}.
 */
@Entity
@Table(name = "tenant_loyalty_settings")
@Getter
@Setter
public class TenantLoyaltySettings {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "ativo", nullable = false)
  private boolean ativo = false;

  @Column(name = "pontos_por_real", nullable = false)
  private BigDecimal pontosPorReal = BigDecimal.ONE;

  @Column(name = "produtos_contam", nullable = false)
  private boolean produtosContam = false;

  /** Configuravel mas nao aplicado automaticamente nesta versao (sem ledger por evento de pontos). */
  @Column(name = "validade_dias")
  private Integer validadeDias;

  @Column(name = "pontos_por_resgate_real", nullable = false)
  private BigDecimal pontosPorResgateReal = new BigDecimal("100");

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
    if (!(o instanceof TenantLoyaltySettings other)) return false;
    return tenantId != null && tenantId.equals(other.tenantId);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
