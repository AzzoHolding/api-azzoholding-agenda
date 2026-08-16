package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Espelha {@code modules/packages/domain/entity/ClientPackagePurchase.java}. Tabela
 * {@code client_package_purchases}.
 */
@Entity
@Table(name = "client_package_purchases")
@Getter
@Setter
public class ClientPackagePurchase {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "client_id", nullable = false)
  private UUID clientId;

  /** Nulo se o pacote de origem foi excluido do catalogo depois da venda. */
  @Column(name = "package_id")
  private UUID packageId;

  /** Snapshot do nome do pacote no momento da venda (sobrevive a edicao/remocao do catalogo). */
  @Column(name = "package_nome", nullable = false)
  private String packageNome;

  @Column(name = "preco_pago", nullable = false)
  private BigDecimal precoPago;

  @Column(name = "comanda_id")
  private UUID comandaId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClientPackagePurchase other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
