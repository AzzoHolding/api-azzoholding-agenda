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
 * Espelha {@code modules/packages/domain/entity/ServicePackageItem.java}. Tabela
 * {@code service_package_items}.
 */
@Entity
@Table(name = "service_package_items")
@Getter
@Setter
public class ServicePackageItem {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "package_id", nullable = false)
  private UUID packageId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "sessoes", nullable = false)
  private int sessoes;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServicePackageItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
