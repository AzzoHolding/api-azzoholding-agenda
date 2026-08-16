package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/packages/domain/entity/ClientPackageBalance.java}. Tabela
 * {@code client_package_balances}.
 */
@Entity
@Table(name = "client_package_balances")
@Getter
@Setter
public class ClientPackageBalance {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "purchase_id", nullable = false)
  private UUID purchaseId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "service_nome", nullable = false)
  private String serviceNome;

  @Column(name = "sessoes_totais", nullable = false)
  private int sessoesTotais;

  @Column(name = "sessoes_usadas", nullable = false)
  private int sessoesUsadas = 0;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClientPackageBalance other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
