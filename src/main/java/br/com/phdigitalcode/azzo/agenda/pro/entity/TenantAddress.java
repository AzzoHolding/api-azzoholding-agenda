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
 * Espelha {@code domain/entity/TenantAddress.java}. Tabela {@code tenant_addresses} — a PK e o
 * proprio {@code tenant_id} (um endereco por tenant).
 *
 * <p>A associacao {@code @OneToOne}/{@code @MapsId} para {@code Tenant} do original nao foi
 * portada (mesma adaptacao estrutural ja aplicada em {@code Agendamento}): o service resolve o
 * tenant por repositorio explicito e so precisa do {@code tenantId} escalar para persistir aqui.
 */
@Entity
@Table(name = "tenant_addresses")
@Getter
@Setter
public class TenantAddress {

  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "street")
  private String street;

  @Column(name = "number")
  private String number;

  @Column(name = "complement")
  private String complement;

  @Column(name = "neighborhood")
  private String neighborhood;

  @Column(name = "city")
  private String city;

  @Column(name = "state")
  private String state;

  @Column(name = "zip_code")
  private String zipCode;

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
}
