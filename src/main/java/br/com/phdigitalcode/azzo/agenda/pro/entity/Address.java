package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code domain/entity/Address.java}. Tabela {@code cep_addresses} (cache de CEP). */
@Entity
@Table(name = "cep_addresses")
@Getter
@Setter
public class Address {

  @Id
  @Column(name = "cep", nullable = false, length = 8)
  private String cep;

  @Column(name = "street")
  private String street;

  @Column(name = "complement")
  private String complement;

  @Column(name = "neighborhood")
  private String neighborhood;

  @Column(name = "city")
  private String city;

  @Column(name = "state")
  private String state;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Address other)) return false;
    return cep != null && cep.equals(other.cep);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
