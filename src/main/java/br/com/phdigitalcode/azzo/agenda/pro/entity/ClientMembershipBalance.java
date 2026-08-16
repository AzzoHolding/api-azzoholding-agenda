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
 * Espelha {@code modules/membership/domain/entity/ClientMembershipBalance.java}. Tabela
 * {@code client_membership_balances}.
 */
@Entity
@Table(name = "client_membership_balances")
@Getter
@Setter
public class ClientMembershipBalance {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "membership_id", nullable = false)
  private UUID membershipId;

  @Column(name = "service_id", nullable = false)
  private UUID serviceId;

  @Column(name = "service_nome", nullable = false)
  private String serviceNome;

  @Column(name = "quantidade_mensal", nullable = false)
  private int quantidadeMensal;

  @Column(name = "usadas_no_periodo", nullable = false)
  private int usadasNoPeriodo = 0;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClientMembershipBalance other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
