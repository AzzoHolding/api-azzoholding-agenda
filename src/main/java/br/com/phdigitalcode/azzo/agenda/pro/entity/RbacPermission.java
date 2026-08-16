package br.com.phdigitalcode.azzo.agenda.pro.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/auth/domain/entity/RbacPermission.java}. Tabela {@code permissions}. */
@Entity
@Table(name = "permissions")
@Getter
@Setter
public class RbacPermission {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "description")
  private String description;

  @PrePersist
  void prePersist() {
    if (id == null) id = UUID.randomUUID();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RbacPermission other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
