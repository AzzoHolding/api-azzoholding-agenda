package br.com.phdigitalcode.azzo.agenda.pro.entity;

import br.com.phdigitalcode.azzo.agenda.pro.entity.id.RbacUserRoleId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Espelha {@code modules/auth/domain/entity/RbacUserRole.java}. Tabela {@code user_roles}. */
@Entity
@Table(name = "user_roles")
@Getter
@Setter
public class RbacUserRole {

  @EmbeddedId
  private RbacUserRoleId id;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RbacUserRole other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
